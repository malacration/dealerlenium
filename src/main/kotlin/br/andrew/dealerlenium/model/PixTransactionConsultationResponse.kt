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
    val consultadoEm: String? = null,
    val consultaDesatualizada: Boolean = false,
) {
    companion object {
        /**
         * Monta a resposta a partir do que ja esta persistido, sem consultar o PSP.
         * Usada para transacoes que nao mudam mais de estado (encerradas/expiradas).
         */
        fun fromPersistido(
            transaction: TransactionDocument,
            nomeFavorecido: String?,
        ): PixTransactionConsultationResponse {
            val pago = transaction.status == TransactionStatus.PAGO || transaction.pagamentoConfirmadoEm != null
            return PixTransactionConsultationResponse(
                txId = transaction.txId,
                tipoTransacao = transaction.tipoTransacao,
                originalAmount = transaction.valor,
                paid = pago,
                paymentDate = transaction.pagamentoConfirmadoEm?.toString(),
                receivedAmount = transaction.valor.takeIf { pago },
                status = transaction.status.value,
                valor = transaction.valor,
                descricao = transaction.descricao,
                nomeRecebedor = transaction.clienteNome,
                nomeFavorecido = nomeFavorecido,
                qrCode = transaction.qrCode,
                qrCodeBase64 = transaction.qrCodeBase64,
                expiracaoEm = DateTimeFormatter.ISO_INSTANT.format(transaction.pixExpiraEm),
                consultadoEm = transaction.ultimaVerificacaoEm?.toString(),
            )
        }

        fun from(
            transaction: TransactionDocument,
            providerStatus: UzziPixTransactionStatus,
            nomeFavorecido: String?,
        ): PixTransactionConsultationResponse {
            val resolvedStatus = when (transaction.status) {
                TransactionStatus.CRIADO -> providerStatus.status ?: transaction.status.value
                else -> transaction.status.value
            }
            return PixTransactionConsultationResponse(
                txId = providerStatus.txId ?: transaction.txId,
                tipoTransacao = transaction.tipoTransacao,
                originalAmount = providerStatus.originalAmount?.takeIf { it.signum() != 0 } ?: transaction.valor,
                paid = providerStatus.resolvedPaid() ?: false,
                paymentDate = providerStatus.paymentDate?.toInstant()?.toString(),
                paymentType = providerStatus.paymentType,
                receivedAmount = providerStatus.receivedAmount,
                status = resolvedStatus,
                valor = transaction.valor,
                descricao = transaction.descricao,
                nomeRecebedor = transaction.clienteNome,
                nomeFavorecido = nomeFavorecido,
                qrCode = transaction.qrCode,
                qrCodeBase64 = transaction.qrCodeBase64,
                expiracaoEm = DateTimeFormatter.ISO_INSTANT.format(transaction.pixExpiraEm),
                consultadoEm = providerStatus.consultadoEm?.toString(),
                consultaDesatualizada = providerStatus.desatualizado,
            )
        }
    }
}
