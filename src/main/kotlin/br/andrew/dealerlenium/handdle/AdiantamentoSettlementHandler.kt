package br.andrew.dealerlenium.handdle

import br.andrew.dealerlenium.model.PixTransactionConsultationResponse
import br.andrew.dealerlenium.model.TipoTransacao
import br.andrew.dealerlenium.model.TransactionDocument
import br.andrew.dealerlenium.service.AdiantamentoService
import org.springframework.stereotype.Component

@Component
class AdiantamentoSettlementHandler(
    private val adiantamentoService: AdiantamentoService,
) : TransactionSettlementHandler {
    override fun supports(tipoTransacao: TipoTransacao): Boolean = tipoTransacao == TipoTransacao.ADIANTAMENTO

    override fun baixa(transaction: TransactionDocument, pagamento: PixTransactionConsultationResponse) {
        adiantamentoService.baixaAdiantamento(transaction, pagamento)
    }
}
