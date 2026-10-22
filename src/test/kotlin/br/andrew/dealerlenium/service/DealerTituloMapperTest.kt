package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.model.SituacaoContaReceber
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.*

class DealerTituloMapperTest {
    private val mapper = DealerTituloMapper()
    private val empresa = empresasReceber().getEmpresaOrThrow("FILIAL_A")

    @Test
    fun `normaliza codigo do cliente com zeros decimais como inteiro`() {
        for (raw in listOf("4", "4.00", "4,00")) {
            val row = (tituloWs() as ObjectNode).put("Titulo_PessoaCod", raw)
            assertEquals("4", assertNotNull(mapper.map(row, empresa)).clienteId)
        }
        val numeric = (tituloWs() as ObjectNode).put("Titulo_PessoaCod", BigDecimal("4.00"))
        assertEquals("4", assertNotNull(mapper.map(numeric, empresa)).clienteId)
    }

    @Test
    fun `nao trunca codigo fracionario no backend para evitar identificar outro cliente`() {
        val row = (tituloWs() as ObjectNode).put("Titulo_PessoaCod", "4.90")
        assertFailsWith<ResponseStatusException> { mapper.map(row, empresa) }
    }

    @Test
    fun `normaliza o formato real do WS com saldo parcial e data com horario`() {
        val conta = assertNotNull(mapper.map(tituloWs(), empresa))
        assertEquals(BigDecimal("50.00"), conta.saldoAberto)
        assertEquals(BigDecimal("100.00"), conta.valorOriginal)
        assertEquals(SituacaoContaReceber.PARCIAL, conta.situacao)
        assertEquals("José Exemplo", conta.clienteNome)
        assertEquals("2", conta.parcela)
        assertEquals("FILIAL_A", conta.empresaId)
        assertEquals(LocalDate.of(2026, 10, 20), conta.vencimento)
        assertTrue(conta.podeGerarPix)
    }

    @Test
    fun `nao expoe outra filial mesmo se WS ignorar filtro`() {
        assertNull(mapper.map(tituloWs(empresa = 2), empresa))
    }

    @Test
    fun `nao infere saldo se WS omitir o campo`() {
        val row = tituloWs() as ObjectNode
        row.remove("Titulo_Saldo")
        assertFailsWith<ResponseStatusException> { mapper.map(row, empresa) }
    }

    @Test
    fun `titulos pagos cancelados e sem saldo nao podem gerar pix`() {
        for (row in listOf(tituloWs(status = "PAG"), tituloWs(status = "CAN"), tituloWs(saldo = "0.00"))) {
            assertFalse(assertNotNull(mapper.map(row, empresa)).podeGerarPix)
        }
    }

    @Test
    fun `recusa status desconhecido valores invalidos e data invalida`() {
        assertFailsWith<ResponseStatusException> { mapper.map(tituloWs(status = "NOVO"), empresa) }
        assertFailsWith<ResponseStatusException> { mapper.map(tituloWs(saldo = "x"), empresa) }
        assertFailsWith<ResponseStatusException> { mapper.map(tituloWs(saldo = "-1.00"), empresa) }
        val row = (tituloWs() as ObjectNode).put("Titulo_DataVencimento", "0000-00-00")
        assertFailsWith<ResponseStatusException> { mapper.map(row, empresa) }
    }
}
