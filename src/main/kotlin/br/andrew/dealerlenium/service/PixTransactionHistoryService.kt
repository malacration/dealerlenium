package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.model.Cliente
import br.andrew.dealerlenium.infrastructure.configurations.EmpresaProperties
import br.andrew.dealerlenium.model.PixPagamentoResponse
import br.andrew.dealerlenium.model.TransactionDocument
import br.andrew.dealerlenium.model.TransactionParameters
import br.andrew.dealerlenium.model.TipoTransacao
import br.andrew.dealerlenium.pages.ContasReceberRegistro
import br.andrew.dealerlenium.repositorys.TransactionRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PixTransactionHistoryService(
    private val empresaProperties: EmpresaProperties,
    private val transactionRepository: TransactionRepository,
    private val monitoringPolicy: TransactionMonitoringPolicy,
) {
    fun salvarHistoricoPix(
        conta: ContasReceberRegistro,
        pixPagamentoResponse: PixPagamentoResponse,
        retornoPix: DataRetonroPixQrCode,
    ) {
        val empresa = conta.empresa ?: throw IllegalArgumentException("Empresa nao foi extraida do dealernet")
        val empresaConfigurada = empresaProperties.getEmpresaOrThrow(empresa)

        val documento = TransactionDocument(
            pixPagamentoResponse = pixPagamentoResponse,
            conta = conta,
            reference = retornoPix.data.reference,
            pixGeradoEm = retornoPix.data.generatedAt?.toInstant(),
            cnpjContaRecebimento = empresaConfigurada.cnpj,
            chavePixRecebimento = empresaConfigurada.chavePix,
            nomeTitularContaRecebimento = empresaConfigurada.titular,
            proximaVerificacaoEm = monitoringPolicy.proximaExecucao(
                pixPagamentoResponse.tipoTransacao,
                retornoPix.data.generatedAt?.toInstant() ?: Instant.now(),
            ),
        )

        transactionRepository.save(documento)
    }

    fun salvarHistoricoPixAdiantamento(
        empresa: String,
        cliente: Cliente,
        pixPagamentoResponse: PixPagamentoResponse,
        retornoPix: DataRetonroPixQrCode,
        parametros: TransactionParameters? = null,
    ): TransactionDocument {
        val empresaConfigurada = empresaProperties.getEmpresaOrThrow(empresa)

        val documento = TransactionDocument(
            id = null,
            idLancamento = cliente.codigo,
            tipoTransacao = TipoTransacao.ADIANTAMENTO,
            txId = pixPagamentoResponse.txId,
            reference = retornoPix.data.reference,
            empresa = empresa,
            empresaCod = null,
            clienteNome = cliente.nome ?: cliente.nomeFantasia ?: "Cliente ${cliente.codigo}",
            clienteCodigo = cliente.codigo.toString(),
            valor = pixPagamentoResponse.valor,
            status = "Criado",
            statusTituloRef = "",
            descricao = pixPagamentoResponse.descricao,
            parametros = parametros,
            numeroParcela = null,
            vencimentoTitulo = null,
            pixGeradoEm = retornoPix.data.generatedAt?.toInstant() ?: Instant.now(),
            pixExpiraEm = Instant.parse(pixPagamentoResponse.expiracaoEm),
            qrCode = pixPagamentoResponse.qrCode,
            qrCodeBase64 = pixPagamentoResponse.qrCodeBase64,
            cnpjContaRecebimento = empresaConfigurada.cnpj,
            chavePixRecebimento = empresaConfigurada.chavePix,
            nomeTitularContaRecebimento = empresaConfigurada.titular,
            proximaVerificacaoEm = monitoringPolicy.proximaExecucao(
                pixPagamentoResponse.tipoTransacao,
                retornoPix.data.generatedAt?.toInstant() ?: Instant.now(),
            ),
        )

        return transactionRepository.save(documento)
    }
}
