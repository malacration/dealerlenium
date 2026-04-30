package br.andrew.dealerlenium.model

import br.andrew.dealerlenium.service.DataRetonroPixQrCode
import java.math.BigDecimal

data class PixPagamentoResponse(
    val idLancamento: Int,
    val tipoTransacao: TipoTransacao = TipoTransacao.CONTAS_RECEBER,
    val txId: String,
    val status: String,
    val valor: BigDecimal,
    val descricao: String,
    val nomeRecebedor: String,
    val qrCode: String,
    val qrCodeBase64: String,
    val expiracaoEm: String,
) {
    lateinit var documento: TransactionDocument

    constructor(
        idLancamento: Int,
        tipoTransacao: TipoTransacao = TipoTransacao.CONTAS_RECEBER,
        status: String,
        valor: BigDecimal,
        descricao: String,
        nomeRecebedor: String,
        expiracaoEm: String,
        retornoPix: DataRetonroPixQrCode,
    ) : this(
        idLancamento = idLancamento,
        tipoTransacao = tipoTransacao,
        txId = retornoPix.data.reference,
        status = status,
        valor = valor,
        descricao = descricao,
        nomeRecebedor = nomeRecebedor,
        qrCode = retornoPix.data.textContent,
        qrCodeBase64 = retornoPix.data.link,
        expiracaoEm = expiracaoEm,
    )
}
