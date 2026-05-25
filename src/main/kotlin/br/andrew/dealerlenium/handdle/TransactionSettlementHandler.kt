package br.andrew.dealerlenium.handdle

import br.andrew.dealerlenium.model.PixTransactionConsultationResponse
import br.andrew.dealerlenium.model.TipoTransacao
import br.andrew.dealerlenium.model.TransactionDocument
import org.springframework.stereotype.Component

interface TransactionSettlementHandler {
    fun supports(tipoTransacao: TipoTransacao): Boolean
    fun baixa(transaction: TransactionDocument, pagamento: PixTransactionConsultationResponse): TransactionSettlementResult
}

data class TransactionSettlementResult(
    val settlementId: Int? = null,
)

@Component
class TransactionSettlementDispatcher(
    private val handlers: List<TransactionSettlementHandler>,
) {
    fun baixa(transaction: TransactionDocument, pagamento: PixTransactionConsultationResponse): TransactionSettlementResult {
        val handler = handlers.firstOrNull { it.supports(transaction.tipoTransacao) }
            ?: throw IllegalStateException("Nao existe baixa configurada para o tipo ${transaction.tipoTransacao}")

        return handler.baixa(transaction, pagamento)
    }
}
