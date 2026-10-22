package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.infrastructure.configurations.Empresa
import br.andrew.dealerlenium.model.ContaReceber
import br.andrew.dealerlenium.model.SituacaoContaReceber
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Component
class DealerTituloMapper {
    fun map(row: JsonNode, empresa: Empresa): ContaReceber? {
        val fields = row.properties().associate { normalize(it.key) to it.value }
        fun text(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
            fields[normalize(name)]?.takeUnless { it.isNull }?.asText()?.trim()?.takeIf { it.isNotEmpty() }
        }
        fun required(vararg names: String): String = text(*names) ?: invalid("Campo ${names.first()} ausente no retorno de titulos.")
        fun money(name: String): BigDecimal {
            val raw = required(name)
            val normalized = if (',' in raw) raw.replace(".", "").replace(',', '.') else raw
            return try {
                normalized.toBigDecimal().setScale(2, RoundingMode.UNNECESSARY)
                    .also { if (it.signum() < 0) invalid("Campo $name negativo no retorno de titulos.") }
            } catch (ex: NumberFormatException) {
                invalid("Campo $name invalido no retorno de titulos.")
            } catch (ex: ArithmeticException) {
                invalid("Campo $name com precisao invalida no retorno de titulos.")
            }
        }
        fun date(name: String, required: Boolean = true): LocalDate? {
            val value = text(name)?.takeUnless { it == "0000-00-00" }
            if (value == null && !required) return null
            return runCatching { LocalDate.parse(value?.substringBefore('T')) }.getOrNull()
                ?: invalid("Campo $name invalido no retorno de titulos.")
        }

        val empresaCodigo = required("Titulo_EmpresaCod").toIntOrNull()
            ?: invalid("Codigo da empresa invalido no retorno de titulos.")
        // A autorização também é aplicada ao resultado, caso o WS ignore o filtro.
        if (empresaCodigo != empresa.codigoDealer) return null
        if (required("Titulo_MovimentoFinanceiro").uppercase() != "R") return null
        val id = required("Titulo_Codigo").toIntOrNull()?.takeIf { it > 0 }
            ?: invalid("Codigo do titulo invalido.")
        val valor = money("Titulo_Valor")
        // Nunca deduzir saldo do valor original ou das movimentações: é o valor cobrado.
        val saldo = money("Titulo_Saldo")
        val status = required("Titulo_Status").uppercase()
        val situacao = when (status) {
            "CAN" -> SituacaoContaReceber.CANCELADO
            "PAG" -> SituacaoContaReceber.PAGO
            "AUT", "PEN", "ENB" -> when {
                saldo.signum() == 0 -> SituacaoContaReceber.PAGO
                saldo < valor -> SituacaoContaReceber.PARCIAL
                else -> SituacaoContaReceber.ABERTO
            }
            else -> invalid("Status de titulo nao reconhecido no Web Service.")
        }
        val clienteId = try {
            required("Titulo_PessoaCod").replace(',', '.').toBigDecimal().toBigIntegerExact()
                .takeIf { it.signum() > 0 }?.toString() ?: invalid("Codigo do cliente invalido no retorno de titulos.")
        } catch (ex: NumberFormatException) {
            invalid("Codigo do cliente invalido no retorno de titulos.")
        } catch (ex: ArithmeticException) {
            invalid("Codigo do cliente deve ser inteiro no retorno de titulos.")
        }
        return ContaReceber(
            tituloId = id.toString(),
            empresaId = empresa.id,
            empresaNome = empresa.descricao?.takeIf { it.isNotBlank() } ?: empresa.id,
            documento = required("Titulo_Numero"),
            parcela = text("Titulo_Parcela") ?: text("Titulo_NumeroParcela")?.substringAfterLast('-'),
            clienteId = clienteId,
            clienteNome = text("Titulo_PessoaNome", "Titulo_PessoaNom") ?: "Cliente $clienteId",
            clienteDocumento = text("Titulo_PessoaDocumento", "Titulo_PessoaDocIdentificador"),
            emissao = date("Titulo_DataEmissao", required = false),
            vencimento = date("Titulo_DataVencimento")!!,
            valorOriginal = valor,
            saldoAberto = saldo,
            situacao = situacao,
            podeGerarPix = saldo.signum() > 0 && situacao in setOf(SituacaoContaReceber.ABERTO, SituacaoContaReceber.PARCIAL),
        )
    }

    private fun normalize(name: String) = name.replace("_", "").lowercase()
    private fun invalid(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_GATEWAY, message)
}
