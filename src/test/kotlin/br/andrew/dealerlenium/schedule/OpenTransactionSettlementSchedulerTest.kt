package br.andrew.dealerlenium.schedule

import br.andrew.dealerlenium.DealerProperties
import br.andrew.dealerlenium.anyArg
import br.andrew.dealerlenium.TransactionMonitoringProperties
import br.andrew.dealerlenium.model.TipoTransacao
import br.andrew.dealerlenium.model.TransactionDocument
import br.andrew.dealerlenium.repositorys.TransactionRepository
import br.andrew.dealerlenium.service.OpenTransactionSettlementProcessor
import br.andrew.dealerlenium.service.TransactionMonitoringPolicy
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals

class OpenTransactionSettlementSchedulerTest {

    private val transactionRepository = mock(TransactionRepository::class.java)
    private val settlementProcessor = mock(OpenTransactionSettlementProcessor::class.java)
    private val sleeper = mock(Sleeper::class.java)
    private val dealerProperties = DealerProperties(
        loginUrl = "http://localhost/login",
        indexUrl = "http://localhost/index",
        tipoTitulo = "PIX QR-CODE UZZIPAY",
        transactionMonitoring = TransactionMonitoringProperties(
            batchSize = 3,
            spacing = Duration.ofMillis(250),
        ),
    )
    private val scheduler = OpenTransactionSettlementScheduler(
        dealerProperties,
        TransactionMonitoringPolicy(dealerProperties),
        transactionRepository,
        settlementProcessor,
        sleeper,
    )

    @Test
    fun `busca em lote ordenado e espaca as consultas do ciclo`() {
        val transacoes = listOf(transactionStub("a"), transactionStub("b"), transactionStub("c"))
        given(transactionRepository.findOpenTransactionsReadyForMonitoring(anyArg(), anyArg()))
            .willReturn(transacoes)

        scheduler.processarTransacoesAbertas()

        val pageable = mockingDetails(transactionRepository).invocations
            .first { it.method.name == "findOpenTransactionsReadyForMonitoring" }
            .getArgument<Pageable>(1)
        assertEquals(3, pageable.pageSize)
        assertEquals(Sort.by(Sort.Direction.ASC, "proximaVerificacaoEm"), pageable.sort)

        // uma pausa entre cada par de consultas (nunca antes da primeira)
        verify(sleeper, times(2)).sleep(Duration.ofMillis(250))
        transacoes.forEach { verify(settlementProcessor).process(it.id!!) }
    }

    @Test
    fun `falha de uma transacao nao interrompe as demais`() {
        val transacoes = listOf(transactionStub("a"), transactionStub("b"))
        given(transactionRepository.findOpenTransactionsReadyForMonitoring(anyArg(), anyArg()))
            .willReturn(transacoes)
        given(settlementProcessor.process("a")).willThrow(IllegalStateException("falhou"))

        scheduler.processarTransacoesAbertas()

        verify(settlementProcessor).process("b")
    }

    private fun transactionStub(id: String): TransactionDocument {
        val agora = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        return TransactionDocument(
            id = id,
            idLancamento = 1,
            tipoTransacao = TipoTransacao.ADIANTAMENTO,
            txId = "PIX-$id",
            reference = "REF-$id",
            empresa = "AUTOVEMA_PVH",
            empresaCod = null,
            clienteNome = "Cliente $id",
            clienteCodigo = "1",
            valor = BigDecimal("10.00"),
            descricao = "Teste",
            numeroParcela = null,
            vencimentoTitulo = null,
            pixGeradoEm = agora,
            pixExpiraEm = agora.plus(20, ChronoUnit.MINUTES),
            qrCode = "000201010212",
            qrCodeBase64 = "dGVzdGU=",
            cnpjContaRecebimento = "00000000000191",
            chavePixRecebimento = null,
            nomeTitularContaRecebimento = null,
        )
    }
}
