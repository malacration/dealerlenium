package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.DealerProperties
import br.andrew.dealerlenium.anyArg
import br.andrew.dealerlenium.model.TipoTransacao
import br.andrew.dealerlenium.model.TransactionDocument
import br.andrew.dealerlenium.model.TransactionStatus
import br.andrew.dealerlenium.repositorys.TransactionRepository
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.*
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.*

class ContaReceberPixServiceTest {
    private val empresas = empresasReceber()
    private val empresa = empresas.getEmpresaOrThrow("FILIAL_A")
    private val contas = mock(ContasReceberService::class.java)
    private val uzzi = mock(UzziPixService::class.java)
    private val repository = mock(TransactionRepository::class.java)
    private val agora = Instant.parse("2026-10-20T12:00:00Z")
    private val usuario = usuarioReceber("FILIAL_A")
    private val service = ContaReceberPixService(
        contas, uzzi, repository, empresas,
        TransactionMonitoringPolicy(DealerProperties(loginUrl = "https://example.test", indexUrl = "https://example.test", tipoTitulo = "PIX")),
        CurrentUserService(), Clock.fixed(agora, ZoneOffset.UTC),
    )

    init {
        given(contas.empresaAutorizada("FILIAL_A", usuario)).willReturn(empresa)
        given(contas.consultarTitulo(empresa, 123)).willReturn(contaReceber())
        given(repository.findAllByIdLancamentoAndEmpresaAndTipoTransacaoOrderByCreatedAtDesc(123, "FILIAL_A", TipoTransacao.CONTAS_RECEBER))
            .willReturn(emptyList())
    }

    @Test
    fun `cobra saldo reconsultado e persiste documento para monitoramento e compartilhamento`() {
        given(uzzi.createPixByEmpresa(anyArg(), anyArg(), anyArg())).willReturn(DataRetonroPixQrCode(
            RetonroPixQrCode("pix-code", "base64", "reference-123", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC)),
        ))
        given(repository.save(anyArg<TransactionDocument>())).willAnswer { call -> (call.arguments[0] as TransactionDocument).copy(id = "mongo-id") }

        val response = service.gerar(123, "FILIAL_A", usuario)

        assertEquals(BigDecimal("50.00"), response.valor)
        assertEquals("mongo-id", response.documento.id)
        assertEquals("FILIAL_A", response.documento.empresa)
        assertEquals("1", response.documento.empresaCod)
        assertEquals(agora.plusSeconds(3600), response.documento.pixExpiraEm)
        assertNotNull(response.documento.proximaVerificacaoEm)
        val chamada = mockingDetails(uzzi).invocations.single()
        assertEquals("FILIAL_A", chamada.arguments[0])
        assertEquals(BigDecimal("50.00"), chamada.arguments[2])
    }

    @Test
    fun `reutiliza pix vigente da mesma filial titulo e tipo`() {
        val existente = transaction()
        given(repository.findAllByIdLancamentoAndEmpresaAndTipoTransacaoOrderByCreatedAtDesc(123, "FILIAL_A", TipoTransacao.CONTAS_RECEBER))
            .willReturn(listOf(existente))
        val response = service.gerar(123, "FILIAL_A", usuario)
        assertEquals(existente.txId, response.txId)
        assertEquals(existente, response.documento)
        verifyNoInteractions(uzzi)
    }

    @Test
    fun `nao cria nova cobranca se saldo mudou ou ja houve pagamento`() {
        for (existente in listOf(
            transaction().copy(valor = BigDecimal("100.00")),
            transaction().copy(status = TransactionStatus.ERRO_BAIXA),
            transaction().copy(status = TransactionStatus.PAGO),
        )) {
            given(repository.findAllByIdLancamentoAndEmpresaAndTipoTransacaoOrderByCreatedAtDesc(123, "FILIAL_A", TipoTransacao.CONTAS_RECEBER))
                .willReturn(listOf(existente))
            assertFailsWith<ResponseStatusException> { service.gerar(123, "FILIAL_A", usuario) }
        }
        verifyNoInteractions(uzzi)
    }

    @Test
    fun `nao gera pix para titulo sem saldo ou acesso negado`() {
        given(contas.consultarTitulo(empresa, 123)).willReturn(contaReceber().copy(saldoAberto = BigDecimal.ZERO, podeGerarPix = false))
        assertFailsWith<ResponseStatusException> { service.gerar(123, "FILIAL_A", usuario) }
        given(contas.empresaAutorizada("FILIAL_A", usuario)).willThrow(ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
        assertFailsWith<ResponseStatusException> { service.gerar(123, "FILIAL_A", usuario) }
        verifyNoInteractions(uzzi)
    }

    private fun transaction() = TransactionDocument(
        id = "existing", idLancamento = 123, txId = "reference", reference = "reference", empresa = "FILIAL_A", empresaCod = "1",
        clienteNome = "Cliente", clienteCodigo = "789", valor = BigDecimal("50.00"), descricao = "Titulo",
        numeroParcela = "2", vencimentoTitulo = "2026-10-20", pixGeradoEm = agora, pixExpiraEm = agora.plusSeconds(3600),
        qrCode = "pix", qrCodeBase64 = "base64", cnpjContaRecebimento = "00000000000191", chavePixRecebimento = null,
        nomeTitularContaRecebimento = null,
    )
}
