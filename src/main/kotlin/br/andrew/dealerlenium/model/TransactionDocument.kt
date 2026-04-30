package br.andrew.dealerlenium.model

import br.andrew.dealerlenium.pages.ContasReceberRegistro
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.math.BigDecimal
import java.time.Instant
import java.time.format.DateTimeFormatter

@Document(collection = "transaction")
data class TransactionDocument(
    @Id
    val id: String? = null,
    val idLancamento: Int,
    val tipoTransacao: TipoTransacao = TipoTransacao.CONTAS_RECEBER,
    val txId: String,
    val reference: String,
    val empresa: String,
    val empresaCod: String?,
    val clienteNome: String,
    val clienteCodigo: String?,
    val valor: BigDecimal,
    val status: String = "Criado",
    @Field("statusTitulo")
    val statusTituloRef: String = "",
    val descricao: String,
    val numeroParcela: String?,
    val vencimentoTitulo: String?,
    val pixGeradoEm: Instant,
    val pixExpiraEm: Instant,
    val qrCode: String,
    val qrCodeBase64: String,
    val cnpjContaRecebimento: String,
    val chavePixRecebimento: String?,
    val nomeTitularContaRecebimento: String?,
    val createdAt: Instant = Instant.now(),
) {
    constructor(
        pixPagamentoResponse: PixPagamentoResponse,
        conta: ContasReceberRegistro,
        reference: String,
        pixGeradoEm: Instant?,
        cnpjContaRecebimento: String,
        chavePixRecebimento: String?,
        nomeTitularContaRecebimento: String?,
    ) : this(
        id = null,
        idLancamento = pixPagamentoResponse.idLancamento,
        tipoTransacao = pixPagamentoResponse.tipoTransacao,
        txId = pixPagamentoResponse.txId,
        reference = reference,
        empresa = conta.empresa ?: throw IllegalArgumentException("Empresa nao foi extraida do dealernet"),
        empresaCod = conta.empresaCod,
        clienteNome = pixPagamentoResponse.nomeRecebedor,
        clienteCodigo = conta.pessoaCod,
        valor = pixPagamentoResponse.valor,
        status = "Criado",
        statusTituloRef = pixPagamentoResponse.status,
        descricao = pixPagamentoResponse.descricao,
        numeroParcela = conta.numeroParcela,
        vencimentoTitulo = conta.vencimento,
        pixGeradoEm = pixGeradoEm ?: Instant.now(),
        pixExpiraEm = Instant.parse(pixPagamentoResponse.expiracaoEm),
        qrCode = pixPagamentoResponse.qrCode,
        qrCodeBase64 = pixPagamentoResponse.qrCodeBase64,
        cnpjContaRecebimento = cnpjContaRecebimento,
        chavePixRecebimento = chavePixRecebimento,
        nomeTitularContaRecebimento = nomeTitularContaRecebimento,
    )

    fun toPixPagamentoResponse(): PixPagamentoResponse {
        return PixPagamentoResponse(
            idLancamento = idLancamento,
            tipoTransacao = tipoTransacao,
            txId = txId,
            status = statusTituloRef,
            valor = valor,
            descricao = descricao,
            nomeRecebedor = clienteNome,
            qrCode = qrCode,
            qrCodeBase64 = qrCodeBase64,
            expiracaoEm = DateTimeFormatter.ISO_INSTANT.format(pixExpiraEm),
        )
    }
}
