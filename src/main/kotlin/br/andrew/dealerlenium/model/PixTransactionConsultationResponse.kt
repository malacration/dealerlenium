package br.andrew.dealerlenium.model

import br.andrew.dealerlenium.service.UzziPixTransactionStatus
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

data class PixTransactionConsultationResponse(
    val txId: String,
    val tipoTransacao: TipoTransacao? = null,
    val originalAmount: BigDecimal? = null,
    val paid: Boolean = false,
    val paymentDate: String? = null,
    val paymentType: String? = null,
    val receivedAmount: BigDecimal? = null,
    val status: String? = null,
    val valor: BigDecimal? = null,
    val descricao: String? = null,
    val nomeRecebedor: String? = null,
    val nomeFavorecido: String? = null,
    val qrCode: String? = null,
    val qrCodeBase64: String? = null,
    val expiracaoEm: String? = null,
) {
    companion object {
        fun from(
            transaction: TransactionDocument,
            providerStatus: UzziPixTransactionStatus,
            nomeFavorecido: String?,
        ): PixTransactionConsultationResponse {
            return PixTransactionConsultationResponse(
                txId = providerStatus.txId ?: transaction.txId,
                tipoTransacao = transaction.tipoTransacao,
                originalAmount = providerStatus.originalAmount ?: transaction.valor,
                paid = providerStatus.resolvedPaid(),
                paymentDate = providerStatus.paymentDate?.toInstant()?.toString(),
                paymentType = providerStatus.paymentType,
                receivedAmount = providerStatus.receivedAmount,
                status = providerStatus.status ?: transaction.status,
                valor = transaction.valor,
                descricao = transaction.descricao,
                nomeRecebedor = transaction.clienteNome,
                nomeFavorecido = nomeFavorecido,
                qrCode = transaction.qrCode,
                qrCodeBase64 = transaction.qrCodeBase64,
                expiracaoEm = DateTimeFormatter.ISO_INSTANT.format(transaction.pixExpiraEm),
            )
        }
    }
}
