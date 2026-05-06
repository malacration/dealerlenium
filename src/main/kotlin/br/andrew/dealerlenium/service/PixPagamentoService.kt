package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.infrastructure.configurations.EmpresaProperties
import br.andrew.dealerlenium.model.PixPagamentoResponse
import br.andrew.dealerlenium.model.PixShareVerificationRequest
import br.andrew.dealerlenium.model.PixTransactionConsultationResponse
import br.andrew.dealerlenium.model.TipoTransacao
import br.andrew.dealerlenium.pages.ContasReceberRegistro
import br.andrew.dealerlenium.repositorys.TransactionRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class PixPagamentoService(
    private val tituloConsultaService: TituloConsultaService,
    private val uzziPixService: UzziPixService,
    private val empresaProperties: EmpresaProperties,
    private val transactionRepository: TransactionRepository,
    private val pixTransactionHistoryService: PixTransactionHistoryService,
) {

    fun gerarPixPagamento(
        idLancamento: Int,
        forcarNovoPix: Boolean,
    ): PixPagamentoResponse {
        if (!forcarNovoPix) {
            buscarPixValido(idLancamento)?.let { return it.toPixPagamentoResponse() }
        }
        val conta = tituloConsultaService.buscarValorTituloDisponivel("all", idLancamento)
        val dadosPagamento = conta.toDadosPagamento()
        val retorno = uzziPixService.createPixByEmpresa(
            dadosPagamento.empresa,
            gerarTxId(),
            dadosPagamento.valor,
        )

        val expiracao = retorno.data.expiresAt
            ?: (retorno.data.generatedAt ?: OffsetDateTime.now(ZoneOffset.UTC)).plusMinutes(15)

        val pixPagamentoResponse = PixPagamentoResponse(
            idLancamento = idLancamento,
            tipoTransacao = TipoTransacao.CONTAS_RECEBER,
            status = conta.status ?: "Desconhecido",
            valor = dadosPagamento.valor,
            descricao = "Parcela numero ${conta.numeroParcela} - Empresa ${dadosPagamento.empresa}",
            nomeRecebedor = dadosPagamento.nomeCliente,
            expiracaoEm = DateTimeFormatter.ISO_INSTANT.format(expiracao.toInstant()),
            retornoPix = retorno,
        )

        pixTransactionHistoryService.salvarHistoricoPix(
            conta = conta,
            pixPagamentoResponse = pixPagamentoResponse,
            retornoPix = retorno,
        )

        return pixPagamentoResponse
    }

    fun verificarPixCompartilhado(request: PixShareVerificationRequest): PixTransactionConsultationResponse {
        val txId = request.txId.trim()
            .takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("TxID do pagamento nao foi informado.")
        val transaction = transactionRepository.findFirstByTxIdOrderByCreatedAtDesc(txId)
            ?: throw IllegalArgumentException("Nao foi possivel confirmar a transacao PIX informada.")

        validateSharedPayment(request, transaction)
        val providerStatus = uzziPixService.verifica(transaction.empresa, transaction.reference)

        return PixTransactionConsultationResponse.from(transaction, providerStatus, resolveNomeFavorecido(transaction))
    }

    fun consultarPagamentoDaTransacao(id: String): PixTransactionConsultationResponse {
        val transaction = transactionRepository.findById(id).orElseThrow {
            IllegalArgumentException("Erro ao recuperar $id")
        }
        val providerStatus = uzziPixService.verifica(transaction.empresa, transaction.reference)

        return PixTransactionConsultationResponse.from(transaction, providerStatus, resolveNomeFavorecido(transaction))
    }

    private fun buscarPixValido(idLancamento: Int) = transactionRepository
        .findFirstByIdLancamentoAndPixExpiraEmAfterOrderByCreatedAtDesc(
            idLancamento,
            Instant.now(),
        )

    private fun validateSharedPayment(
        request: PixShareVerificationRequest,
        transaction: br.andrew.dealerlenium.model.TransactionDocument,
    ) {
        val qrCode = request.qrCode?.trim().orEmpty()
        if (qrCode.isNotEmpty() && qrCode != transaction.qrCode.trim()) {
            throw IllegalArgumentException("Os dados do link do pagamento sao invalidos.")
        }

        request.valor?.let { valor ->
            if (valor.compareTo(transaction.valor) != 0) {
                throw IllegalArgumentException("Os dados do link do pagamento sao invalidos.")
            }
        }

        val vencimento = request.vencimento?.trim().orEmpty()
        if (vencimento.isEmpty() && parseInstant(vencimento).compareTo(transaction.pixExpiraEm) != 0) {
            throw IllegalArgumentException("Os dados do link do pagamento sao invalidos.")
        }
    }

    private fun parseInstant(value: String): Instant {
        return runCatching { Instant.parse(value) }
            .recoverCatching { OffsetDateTime.parse(value).toInstant() }
            .getOrElse { throw IllegalArgumentException("Os dados do link do pagamento sao invalidos.") }
    }

    private fun gerarTxId(): String = "PIX-${UUID.randomUUID().toString().substring(0, 12).uppercase()}"

    private fun resolveNomeFavorecido(transaction: br.andrew.dealerlenium.model.TransactionDocument): String? {
        return empresaProperties.getDescricaoByEmpresaOrNull(transaction.empresa)
            ?: transaction.empresaCod?.let { empresaProperties.getDescricaoByEmpresaOrNull(it) }
            ?: transaction.nomeTitularContaRecebimento?.trim()?.takeUnless { it.isEmpty() }
            ?: transaction.empresa.takeIf { it.isNotBlank() }
    }

    private fun ContasReceberRegistro.toDadosPagamento(): DadosPagamento {
        val empresa = empresa ?: throw IllegalArgumentException("Empresa nao foi extraida do dealernet")
        val nomeCliente = sacado ?: throw IllegalArgumentException("Nome do sacado nao existe")
        val valor = valor.parseValorMonetario()

        return DadosPagamento(
            empresa = empresa,
            nomeCliente = nomeCliente,
            valor = valor,
        )
    }

    private fun String?.parseValorMonetario(): BigDecimal {
        val valorOriginal = this?.trim().takeUnless { it.isNullOrBlank() }
            ?: throw IllegalArgumentException("Nao foi possivel recuperar o valor")

        val valorNormalizado = if (valorOriginal.contains(",")) {
            valorOriginal.replace(".", "").replace(",", ".")
        } else {
            valorOriginal
        }

        return valorNormalizado.toBigDecimalOrNull()
            ?: throw IllegalArgumentException("Valor '$valorOriginal' invalido para geracao do PIX")
    }
}

private data class DadosPagamento(
    val empresa: String,
    val nomeCliente: String,
    val valor: BigDecimal,
)
