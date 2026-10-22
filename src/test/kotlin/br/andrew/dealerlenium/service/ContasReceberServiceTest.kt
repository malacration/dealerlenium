package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.infrastructure.configurations.EmpresaProperties
import br.andrew.dealerlenium.model.ClienteSelecao
import br.andrew.dealerlenium.model.ContasReceberFiltro
import br.andrew.dealerlenium.model.SituacaoContaReceber
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlin.test.*

class ContasReceberServiceTest {
    private val empresas = empresasReceber()
    private val ws = mock(DealerTituloWebService::class.java)
    private val service = ContasReceberService(empresas, UsuarioEmpresaService(empresas), ws, DealerTituloMapper())
    private val usuario = usuarioReceber("FILIAL_A")

    @Test
    fun `filtros exatos sao enviados ao WS e conferidos mesmo se o WS retornar outros titulos`() {
        val correto = tituloWs()
        val outroDocumento = (tituloWs(124) as ObjectNode).put("Titulo_Numero", 4567)
        val outraParcela = (tituloWs(125) as ObjectNode).put("Titulo_NumeroParcela", "456-20")
        given(ws.consultar(1, inicio, fim, tituloNumero = 456, tituloParcela = 2))
            .willReturn(listOf(correto, outroDocumento, outraParcela))
        val page = service.listar(filtro().copy(documento = 456, parcela = 2), usuario)
        assertEquals(listOf("123"), page.itens.map { it.tituloId })
        given(ws.consultar(1, inicio, fim, tituloCodigo = 123, tituloNumero = 456, tituloParcela = 2))
            .willReturn(listOf(correto, tituloWs(124)))
        assertEquals(listOf("123"), service.listar(filtro().copy(lancamento = 123, documento = 456, parcela = 2), usuario).itens.map { it.tituloId })
    }

    @Test
    fun `numeros invalidos nao chegam ao WS`() {
        for (invalid in listOf(filtro().copy(lancamento = 0), filtro().copy(documento = -1), filtro().copy(parcela = 0))) {
            assertFailsWith<IllegalArgumentException> { service.listar(invalid, usuario) }
        }
        verifyNoInteractions(ws)
    }

    @Test
    fun `consulta pessoa por CPF sem filial e sem consultar titulos`() {
        given(ws.consultarPessoa("12345678900")).willReturn(listOf(ClienteSelecao("4", "Cielo", "12345678900")))
        val page = service.listarClientes(ContasReceberFiltro(busca = "123.456.789-00"), usuarioReceber("FILIAL_B", "FILIAL_A"))
        assertEquals("4", page.itens.single().id)
        verify(ws).consultarPessoa("12345678900")
        verifyNoMoreInteractions(ws)
    }

    @Test
    fun `pessoa nao encontrada retorna lista vazia sem tentar outras filiais`() {
        given(ws.consultarPessoa("12345678900")).willReturn(emptyList())
        assertEquals(0, service.listarClientes(ContasReceberFiltro(busca = "12345678900"), usuarioReceber("FILIAL_A", "FILIAL_B")).total)
        verify(ws).consultarPessoa("12345678900")
        verifyNoMoreInteractions(ws)
    }

    @Test
    fun `consulta de pessoas ignora datas e preserva autorizacao da filial`() {
        given(ws.consultarPessoa("12345678000190")).willReturn(listOf(ClienteSelecao("4", "Cielo", "12345678000190")))
        val page = service.listarClientes(filtro(empresaId = "FILIAL_B", busca = "12.345.678/0001-90", vencimentoDe = fim, vencimentoAte = inicio), usuarioReceber("FILIAL_A", "FILIAL_B"))
        assertEquals("4", page.itens.single().id)
        verify(ws).consultarPessoa("12345678000190")
        verifyNoMoreInteractions(ws)
    }

    @Test
    fun `busca de pessoas rejeita nome codigo e documento incompleto antes do WS`() {
        for (busca in listOf("", "Cielo", "4", "123456", "abc12345678900")) {
            assertFailsWith<IllegalArgumentException> { service.listarClientes(ContasReceberFiltro(busca = busca), usuario) }
        }
        verifyNoInteractions(ws)
    }

    @Test
    fun `consulta de pessoas exige usuario e filial permitida`() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertFailsWith<ResponseStatusException> {
            service.listarClientes(ContasReceberFiltro(busca = "12345678900"), null)
        }.statusCode)
        assertEquals(HttpStatus.FORBIDDEN, assertFailsWith<ResponseStatusException> {
            service.listarClientes(ContasReceberFiltro(busca = "12345678900"), usuarioReceber("ADMIN"))
        }.statusCode)
        verifyNoInteractions(ws)
    }

    @Test
    fun `cliente permite consulta sem datas com codigo exato da pessoa`() {
        val row = (tituloWs() as ObjectNode).put("Titulo_PessoaCod", "4.00")
        given(ws.consultar(1, pessoaCodigo = 4)).willReturn(listOf(row))
        val page = service.listar(filtro(vencimentoDe = null, vencimentoAte = null).copy(clienteId = "4"), usuario)
        assertEquals(1, page.total)
        verify(ws).consultar(1, pessoaCodigo = 4)
        verifyNoMoreInteractions(ws)
    }

    @Test
    fun `cliente permite intervalo longo em todas as filiais autorizadas com codigo exato`() {
        val final = inicio.plusYears(2)
        val rowA = (tituloWs() as ObjectNode).put("Titulo_PessoaCod", "4.00")
        val rowB = (tituloWs(124, empresa = 2) as ObjectNode).put("Titulo_PessoaCod", "4.00")
        given(ws.consultar(1, inicio, final, pessoaCodigo = 4)).willReturn(listOf(rowA))
        given(ws.consultar(2, inicio, final, pessoaCodigo = 4)).willReturn(listOf(rowB))
        val page = service.listar(filtro(empresaId = null, vencimentoAte = final).copy(clienteId = "4"), usuarioReceber("FILIAL_A", "FILIAL_B"))
        assertEquals(2, page.total)
        assertEquals(setOf("FILIAL_A", "FILIAL_B"), page.itens.map { it.empresaId }.toSet())
        verify(ws).consultar(1, inicio, final, pessoaCodigo = 4)
        verify(ws).consultar(2, inicio, final, pessoaCodigo = 4)
        verifyNoMoreInteractions(ws)
    }

    @Test
    fun `todos retorna a selecao completa de titulos para paginacao no frontend`() {
        val rows = (1..25).map { (tituloWs(it) as ObjectNode).put("Titulo_PessoaCod", it) }
        given(ws.consultar(1, inicio, fim)).willReturn(rows)
        assertEquals(20, service.listar(filtro(), usuario).itens.size)
        assertEquals(25, service.listar(filtro(), usuario, todos = true).itens.size)
    }

    @Test
    fun `cliente selecionado e enviado ao WS e comparado por codigo exato no retorno`() {
        val cielo = (tituloWs() as ObjectNode).put("Titulo_PessoaCod", "4.00")
        val outro = (tituloWs(124) as ObjectNode).put("Titulo_PessoaCod", "40.00")
        given(ws.consultar(1, inicio, fim, pessoaCodigo = 4)).willReturn(listOf(cielo, outro))
        val page = service.listar(filtro().copy(clienteId = "4"), usuario)
        assertEquals(1, page.total)
        assertEquals("4", page.itens.single().clienteId)
        verify(ws).consultar(1, inicio, fim, pessoaCodigo = 4)
    }

    @Test
    fun `busca de clientes mantem autorizacao de filial`() {
        assertEquals(HttpStatus.FORBIDDEN, assertFailsWith<ResponseStatusException> {
            service.listarClientes(filtro(empresaId = "FILIAL_B"), usuario)
        }.statusCode)
        verifyNoInteractions(ws)
    }

    @Test
    fun `exige filial e datas e recusa periodo de 31 dias antes de chamar o WS`() {
        for (invalid in listOf(
            filtro(empresaId = null), filtro(empresaId = " "),
            filtro(vencimentoDe = null), filtro(vencimentoAte = null),
            filtro(vencimentoAte = inicio.plusDays(30)),
        )) assertFailsWith<IllegalArgumentException> { service.listar(invalid, usuario) }
        verifyNoInteractions(ws)
    }

    @Test
    fun `id de filial do seletor e convertido para codigo interno do WS`() {
        given(ws.consultar(1, inicio, fim)).willReturn(listOf(tituloWs()))
        val page = service.listar(filtro(empresaId = "FILIAL_A"), usuario)
        assertEquals(1, page.total)
        assertEquals("FILIAL_A", page.itens.single().empresaId)
        verify(ws).consultar(1, inicio, fim)
        verifyNoMoreInteractions(ws)
    }

    @Test
    fun `saldo em aberto inclui recebimentos parciais e exclui pagos cancelados e zerados`() {
        given(ws.consultar(1, inicio, fim)).willReturn(listOf(
            tituloWs(id = 1, saldo = "100.00"),
            tituloWs(id = 2, saldo = "50.00"),
            tituloWs(id = 3, status = "PAG", saldo = "0.00"),
            tituloWs(id = 4, status = "CAN", saldo = "50.00"),
            tituloWs(id = 5, saldo = "0.00"),
        ))
        val page = service.listar(filtro(situacao = SituacaoContaReceber.ABERTO), usuario)
        assertEquals(2, page.total)
        assertEquals(listOf("1", "2"), page.itens.map { it.tituloId })
        assertEquals(listOf(SituacaoContaReceber.ABERTO, SituacaoContaReceber.PARCIAL), page.itens.map { it.situacao })
    }

    @Test
    fun `consulta somente as filiais autorizadas e refiltra retorno do WS`() {
        given(ws.consultar(1, inicio, fim)).willReturn(listOf(tituloWs(), tituloWs(124, empresa = 2)))
        val page = service.listar(filtro(), usuario)
        assertEquals(1, page.total)
        assertEquals("FILIAL_A", page.itens.single().empresaId)
        verify(ws).consultar(1, inicio, fim)
        verifyNoMoreInteractions(ws)
    }

    @Test
    fun `filial sem permissao e bloqueada antes de chamar o WS`() {
        val ex = assertFailsWith<ResponseStatusException> { service.listar(filtro(empresaId = "FILIAL_B"), usuario) }
        assertEquals(HttpStatus.FORBIDDEN, ex.statusCode)
        verifyNoInteractions(ws)
    }

    @Test
    fun `usuario sem filial permitida recebe 403 e anonimo recebe 401`() {
        assertEquals(HttpStatus.FORBIDDEN, assertFailsWith<ResponseStatusException> { service.listar(filtro(), usuarioReceber("ADMIN")) }.statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, assertFailsWith<ResponseStatusException> { service.listar(filtro(), null) }.statusCode)
        verifyNoInteractions(ws)
    }

    @Test
    fun `aplica busca sem acento status e paginacao com total completo`() {
        given(ws.consultar(1, inicio, fim)).willReturn((1..25).reversed().map { tituloWs(id = it) })
        val page = service.listar(filtro(pagina = 1, busca = "jose", situacao = SituacaoContaReceber.PARCIAL), usuario)
        assertEquals(25, page.total)
        assertEquals(5, page.itens.size)
        given(ws.consultar(1, inicio, fim, pessoaDocumento = "12345678900")).willReturn(listOf(tituloWs()))
        val cpfs = service.listar(filtro(busca = "12345678900"), usuario)
        assertEquals(1, cpfs.total)
        verify(ws).consultar(1, inicio, fim, pessoaDocumento = "12345678900")
        assertEquals(0, service.listar(filtro(situacao = SituacaoContaReceber.PAGO), usuario).total)
        assertTrue(service.listar(filtro(pagina = Int.MAX_VALUE), usuario).itens.isEmpty())
    }

    @Test
    fun `valida datas e paginacao antes de chamar o WS`() {
        for (filtro in listOf(
            filtro(pagina = -1), filtro(tamanho = 1000),
            filtro(vencimentoDe = LocalDate.of(2026, 10, 21), vencimentoAte = LocalDate.of(2026, 10, 20)),
        )) assertFailsWith<IllegalArgumentException> { service.listar(filtro, usuario) }
        verifyNoInteractions(ws)
    }

    @Test
    fun `filial sem mapeamento nao e consultada com codigo generico`() {
        val semCodigo = EmpresaProperties(mapOf("FILIAL_A" to empresaReceber(null)))
        val service = ContasReceberService(semCodigo, UsuarioEmpresaService(semCodigo), ws, DealerTituloMapper())
        val ex = assertFailsWith<ResponseStatusException> { service.listar(filtro(), usuario) }
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.statusCode)
        verifyNoInteractions(ws)
    }

    @Test
    fun `busca individual nao aceita titulo de outra filial ou outro codigo`() {
        given(ws.consultar(1, tituloCodigo = 123)).willReturn(listOf(tituloWs(123, empresa = 2), tituloWs(124)))
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> {
            service.consultarTitulo(empresas.getEmpresaOrThrow("FILIAL_A"), 123)
        }.statusCode)
    }
    private val inicio = LocalDate.of(2026, 10, 1)
    private val fim = LocalDate.of(2026, 10, 30)

    private fun filtro(
        pagina: Int = 0, tamanho: Int = 20, busca: String = "", empresaId: String? = "FILIAL_A",
        situacao: SituacaoContaReceber? = null, vencimentoDe: LocalDate? = inicio, vencimentoAte: LocalDate? = fim,
    ) = ContasReceberFiltro(pagina, tamanho, busca, empresaId, situacao, vencimentoDe, vencimentoAte)

}
