package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.DealerProperties
import br.andrew.dealerlenium.anyArg
import br.andrew.dealerlenium.TransactionMonitoringProperties
import br.andrew.dealerlenium.handdle.TransactionSettlementDispatcher
import br.andrew.dealerlenium.model.PixTransactionConsultationResponse
import br.andrew.dealerlenium.model.TipoTransacao
import br.andrew.dealerlenium.model.TransactionDocument
import br.andrew.dealerlenium.model.TransactionStatus
import br.andrew.dealerlenium.repositorys.TransactionRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenTransactionSettlementProcessorTest {

    private val transactionRepository = mock(TransactionRepository::class.java)
    private val pixPagamentoService = mock(PixPagamentoService::class.java)
    private val settlementDispatcher = mock(TransactionSettlementDispatcher::class.java)
    private val processor = OpenTransactionSettlementProcessor(
        transactionRepository,
        pixPagamentoService,
        monitoringPolicy(),
        settlementDispatcher,
    )

    @Test
    fun `falha na consulta reagenda a transacao com backoff`() {
        val transaction = transactionStub()
        given(transactionRepository.findById(TRANSACTION_ID)).willReturn(Optional.of(transaction))
        given(pixPagamentoService.consultarPagamentoDaTransacao(transaction))
            .willThrow(PixProviderRateLimitException(Duration.ofSeconds(60)))

        assertFailsWith<PixProviderRateLimitException> { processor.process(TRANSACTION_ID) }

        val persistida = capturarSave()
        assertEquals(TransactionStatus.CRIADO, persistida.status)
        assertEquals(1, persistida.tentativasConsulta)
        assertNotNull(persistida.proximaVerificacaoEm)
        assertTrue(persistida.proximaVerificacaoEm!!.isAfter(Instant.now()))
        verify(settlementDispatcher, never()).baixa(anyArg(), anyArg())
    }

    @Test
    fun `status desatualizado nao expira a transacao nem dispara a baixa`() {
        val transaction = transactionStub(pixExpiraEm = Instant.now().minus(1, ChronoUnit.MINUTES))
        given(transactionRepository.findById(TRANSACTION_ID)).willReturn(Optional.of(transaction))
        given(pixPagamentoService.consultarPagamentoDaTransacao(transaction))
            .willReturn(consultaStub(paid = true, desatualizada = true))

        processor.process(TRANSACTION_ID)

        val persistida = capturarSave()
        assertEquals(TransactionStatus.CRIADO, persistida.status)
        assertEquals(1, persistida.tentativasConsulta)
        verify(settlementDispatcher, never()).baixa(anyArg(), anyArg())
    }

    @Test
    fun `transacao encerrada responde do banco local sem consultar o PSP`() {
        val transaction = transactionStub(status = TransactionStatus.PAGO)
        given(transactionRepository.findById(TRANSACTION_ID)).willReturn(Optional.of(transaction))
        given(pixPagamentoService.consultarEstadoLocal(transaction))
            .willReturn(consultaStub(paid = true, desatualizada = false))

        processor.process(TRANSACTION_ID)

        verify(pixPagamentoService).consultarEstadoLocal(transaction)
        verify(pixPagamentoService, never()).consultarPagamentoDaTransacao(anyArg(), anyBoolean())
    }

    private fun capturarSave(): TransactionDocument {
        val captor = ArgumentCaptor.forClass(TransactionDocument::class.java)
        verify(transactionRepository).save(captor.capture())
        return captor.value
    }

    private fun monitoringPolicy() = TransactionMonitoringPolicy(
        DealerProperties(
            loginUrl = "http://localhost/login",
            indexUrl = "http://localhost/index",
            tipoTitulo = "PIX QR-CODE UZZIPAY",
            transactionMonitoring = TransactionMonitoringProperties(jitter = Duration.ZERO),
        ),
    )

    private fun consultaStub(paid: Boolean, desatualizada: Boolean) = PixTransactionConsultationResponse(
        txId = "PIX-TESTE",
        tipoTransacao = TipoTransacao.ADIANTAMENTO,
        paid = paid,
        status = if (paid) "Pago" else "Aguardando pagamento",
        consultaDesatualizada = desatualizada,
    )

    private fun transactionStub(
        status: TransactionStatus = TransactionStatus.CRIADO,
        pixExpiraEm: Instant = Instant.now().plus(20, ChronoUnit.MINUTES),
    ): TransactionDocument {
        val agora = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        return TransactionDocument(
            id = TRANSACTION_ID,
            idLancamento = 12345,
            tipoTransacao = TipoTransacao.ADIANTAMENTO,
            txId = "PIX-TESTE",
            reference = "REFTESTE0001",
            empresa = "AUTOVEMA_PVH",
            empresaCod = null,
            clienteNome = "Cliente Teste",
            clienteCodigo = "12345",
            valor = BigDecimal("10.00"),
            status = status,
            descricao = "Teste",
            numeroParcela = null,
            vencimentoTitulo = null,
            pixGeradoEm = agora.minus(10, ChronoUnit.MINUTES),
            pixExpiraEm = pixExpiraEm,
            qrCode = "000201010212",
            qrCodeBase64 = "dGVzdGU=",
            cnpjContaRecebimento = "00000000000191",
            chavePixRecebimento = null,
            nomeTitularContaRecebimento = null,
        )
    }

    private companion object {
        const val TRANSACTION_ID = "transacao-teste-001"
    }
}
