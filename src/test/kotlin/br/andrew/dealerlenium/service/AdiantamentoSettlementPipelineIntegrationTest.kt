package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.model.PixTransactionConsultationResponse
import br.andrew.dealerlenium.model.TransactionStatus
import br.andrew.dealerlenium.model.TipoTransacao
import br.andrew.dealerlenium.model.TransactionDocument
import br.andrew.dealerlenium.repositorys.TransactionRepository
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("dealer-e2e-pipeline")
@EnabledIfEnvironmentVariable(named = "RUN_DEALER_ADIANTAMENTO_PIPELINE_TEST", matches = "true")
class AdiantamentoSettlementPipelineIntegrationTest {

    @Autowired
    private lateinit var settlementProcessor: OpenTransactionSettlementProcessor

    @MockitoBean
    private lateinit var transactionRepository: TransactionRepository

    @MockitoBean
    private lateinit var pixPagamentoService: PixPagamentoService

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun processaBaixaDeAdiantamentoAteOFimNoDealer() {
        val transaction = transactionStub()
        val pagamento = pagamentoConfirmadoStub(transaction)
        val transactionId = transaction.id!!

        given(transactionRepository.findById(transactionId)).willReturn(Optional.of(transaction))
        given(pixPagamentoService.consultarPagamentoDaTransacao(transactionId)).willReturn(pagamento)
        given(transactionRepository.save(org.mockito.ArgumentMatchers.any(TransactionDocument::class.java)))
            .willAnswer { invocation -> invocation.arguments[0] as TransactionDocument }

        val retorno = settlementProcessor.process(transactionId)

        assertTrue(retorno.paid)
        assertEquals(transaction.txId, retorno.txId)

        val captor = ArgumentCaptor.forClass(TransactionDocument::class.java)
        verify(transactionRepository).save(captor.capture())

        val transactionPersistida = captor.value
        assertEquals(TransactionStatus.PAGO, transactionPersistida.status)
        assertNotNull(transactionPersistida.pagamentoConfirmadoEm)
        assertNotNull(transactionPersistida.baixaRealizadaEm)
        assertNotNull(transactionPersistida.encerradaEm)
    }

    private fun transactionStub(): TransactionDocument {
        val agora = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        return TransactionDocument(
            id = "pipeline-adiantamento-001",
            idLancamento = 12345,
            tipoTransacao = TipoTransacao.ADIANTAMENTO,
            txId = "PIX-PIPELINE-ADIANTAMENTO",
            reference = "PIPELINEADTO0001REF",
            empresa = "AUTOVEMA_PVH",
            empresaCod = null,
            clienteNome = "Cliente Pipeline Dealer",
            clienteCodigo = "12345",
            valor = BigDecimal("10.00"),
            status = TransactionStatus.CRIADO,
            statusTituloRef = "",
            descricao = "Teste de pipeline da baixa de adiantamento",
            parametros = null,
            numeroParcela = null,
            vencimentoTitulo = null,
            pixGeradoEm = agora.minus(10, ChronoUnit.MINUTES),
            pixExpiraEm = agora.plus(20, ChronoUnit.MINUTES),
            qrCode = "000201010212",
            qrCodeBase64 = "cGlwZWxpbmU=",
            cnpjContaRecebimento = "00000000000191",
            chavePixRecebimento = null,
            nomeTitularContaRecebimento = null,
            createdAt = agora.minus(10, ChronoUnit.MINUTES),
            ultimaVerificacaoEm = null,
            proximaVerificacaoEm = agora.minus(1, ChronoUnit.MINUTES),
            pagamentoConfirmadoEm = null,
            baixaRealizadaEm = null,
            idBaixa = null,
            encerradaEm = null,
            ultimaFalhaProcessamento = null,
        )
    }

    private fun pagamentoConfirmadoStub(transaction: TransactionDocument): PixTransactionConsultationResponse {
        return PixTransactionConsultationResponse(
            txId = transaction.txId,
            tipoTransacao = transaction.tipoTransacao,
            paid = true,
            paymentDate = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            status = "Pago",
            valor = transaction.valor,
            descricao = transaction.descricao,
            nomeRecebedor = transaction.clienteNome,
            expiracaoEm = transaction.pixExpiraEm.toString(),
        )
    }
}
