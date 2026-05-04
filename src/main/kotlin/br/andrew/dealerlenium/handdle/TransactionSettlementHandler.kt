package br.andrew.dealerlenium.handdle

import br.andrew.dealerlenium.model.PixTransactionConsultationResponse
import br.andrew.dealerlenium.model.TipoTransacao
import br.andrew.dealerlenium.model.TransactionDocument
import org.springframework.stereotype.Component

interface TransactionSettlementHandler {
    fun supports(tipoTransacao: TipoTransacao): Boolean
    fun baixa(transaction: TransactionDocument, pagamento: PixTransactionConsultationResponse)
}

@Component
class TransactionSettlementDispatcher(
    private val handlers: List<TransactionSettlementHandler>,
) {
    fun baixa(transaction: TransactionDocument, pagamento: PixTransactionConsultationResponse) {
        val handler = handlers.firstOrNull { it.supports(transaction.tipoTransacao) }
            ?: throw IllegalStateException("Nao existe baixa configurada para o tipo ${transaction.tipoTransacao}")

        handler.baixa(transaction, pagamento)
    }
}
