package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.browser.BrowserRuntime
import br.andrew.dealerlenium.controller.PixClienteAdvanceRequest
import br.andrew.dealerlenium.infrastructure.configurations.EmpresaProperties
import br.andrew.dealerlenium.model.Cliente
import br.andrew.dealerlenium.model.PixPagamentoResponse
import br.andrew.dealerlenium.model.TransactionParameters
import br.andrew.dealerlenium.model.TipoTransacao
import br.andrew.dealerlenium.pages.NavigationPage
import br.andrew.dealerlenium.pages.SelenideElementHelper
import com.codeborne.selenide.Condition.visible
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class PessoaService(
    private val browserSessionManager: BrowserSessionManager,
    private val uzziPixService: UzziPixService,
    private val nav : NavigationPage,
    private val pixTransactionHistoryService: PixTransactionHistoryService,
    private val empresaProperties: EmpresaProperties,
) {

    fun getCliente(idCliente: Int): Cliente {
        return browserSessionManager.runInSession() { homePage ->
            nav.goPessoas(homePage)
            BrowserRuntime.css(
                SelenideElementHelper.selectorByAnyIdContains(
                    "vPESSOA_CODIGO",
                    "vPESSOA_CODIGOGRID",
                )
            ).shouldBe(visible).setValue(idCliente.toString())
            BrowserRuntime.css("#IMGREFRESH").shouldBe(visible).click()

            homePage.waitAjaxLoadingToFinish()
            // Espera por conteudo: o grid precisa refletir exatamente o id pedido antes
            // de ler os demais campos, evitando leitura da linha anterior (Francisco/Sabrina).
            val codigoText = SelenideElementHelper.waitForTextByAnyIdContains(
                idCliente.toString(),
                "span_PESSOA_CODIGO",
                "vPESSOA_CODIGOGRID",
            )
            val codigo = codigoText?.toIntOrNull()
                ?: throw IllegalArgumentException("Cliente $idCliente nao encontrado")
            if (codigo != idCliente) {
                throw StaleDealerReadException(
                    "Grid de pessoas retornou o codigo $codigo para a busca de $idCliente",
                )
            }
            throwIfCadastroAlert()

            Cliente(
                codigo = codigo,
                nome = SelenideElementHelper.textOrNullByIdContains("PESSOA_NOMEGRID"),
                nomeFantasia = SelenideElementHelper.textOrNullByIdContains("PESSOA_NOMEFANTASIAGRID"),
                cpfCnpj = SelenideElementHelper.textOrNullByIdContains("PESSOA_DOCIDENTIFICADORGRID"),
                municipio = SelenideElementHelper.textOrNullByIdContains("PESSOAENDERECO_MUNICIPIONOM"),
                uf = SelenideElementHelper.textOrNullByIdContains("PESSOAENDERECO_ESTADOCOD"),
                ativo = SelenideElementHelper.isCheckedByNameContains("PESSOA_ATIVO")
            )
        }
    }

    fun gerarPixAdiantamento(request: PixClienteAdvanceRequest): PixPagamentoResponse {
        val idCliente = request.idCliente.toIntOrNull()
            ?: throw IllegalArgumentException("ID do cliente invalido")
        val empresa = request.branchId.trim()
            .takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Filial deve ser informada")
        val valor = request.valor
            ?: throw IllegalArgumentException("Valor do adiantamento deve ser informado")

        val cliente = getCliente(idCliente)
        empresaProperties.getEmpresaOrThrow(empresa)
        val retorno = uzziPixService.createPixByEmpresa(
            empresa,
            gerarTxId(),
            valor,
        )
        val expiracao = retorno.data.expiresAt
            ?: (retorno.data.generatedAt ?: OffsetDateTime.now(ZoneOffset.UTC)).plusHours(12)
        val nomeCliente = cliente.nome ?: cliente.nomeFantasia ?: "Cliente $idCliente"

        val pixPagamentoResponse = PixPagamentoResponse(
            idLancamento = idCliente,
            tipoTransacao = TipoTransacao.ADIANTAMENTO,
            status = "ADIANTAMENTO",
            valor = valor,
            descricao = "Adiantamento cliente $idCliente - Empresa $empresa",
            nomeRecebedor = nomeCliente,
            expiracaoEm = DateTimeFormatter.ISO_INSTANT.format(expiracao.toInstant()),
            retornoPix = retorno,
        )

        val documento = pixTransactionHistoryService.salvarHistoricoPixAdiantamento(
            empresa = empresa,
            cliente = cliente,
            pixPagamentoResponse = pixPagamentoResponse,
            retornoPix = retorno,
            parametros = TransactionParameters(
                idCliente = request.idCliente,
                branchId = request.branchId,
                valor = valor,
                departamentoCod = request.departamentoCod,
                tipoFichaRazaoCod = request.tipoFichaRazaoCod,
            ),
        )

        return pixPagamentoResponse.also {
            it.documento = documento
        }
    }

    private fun gerarTxId(): String = "PIX-${UUID.randomUUID().toString().substring(0, 12).uppercase()}"

    private fun throwIfCadastroAlert() {
        val statusElement = BrowserRuntime.css(
            "input[type='image'][id*='vSTATUS'], img[id*='vSTATUS'], " +
                "input[type='image'][name*='vSTATUS'], img[name*='vSTATUS']"
        )
        if (!statusElement.exists()) {
            return
        }

        val alertMessage = PessoaGridStatusAlert.messageOrNull(
            src = statusElement.getAttribute("src"),
            title = statusElement.getAttribute("title"),
        ) ?: return

        throw IllegalArgumentException(alertMessage)
    }
}

object PessoaGridStatusAlert {
    fun messageOrNull(src: String?, title: String?): String? {
        if (!src.orEmpty().contains("IcoAlerta", ignoreCase = true)) {
            return null
        }

        return title?.trim()?.takeIf { it.isNotEmpty() } ?: "Pendencias de Cadastro"
    }
}
