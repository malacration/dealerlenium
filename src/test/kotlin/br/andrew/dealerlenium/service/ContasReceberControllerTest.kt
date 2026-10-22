package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.anyArg
import br.andrew.dealerlenium.controller.ContasReceberController
import br.andrew.dealerlenium.infrastructure.ApiExceptionHandler
import br.andrew.dealerlenium.model.ContasReceberPagina
import br.andrew.dealerlenium.model.ClienteSelecao
import br.andrew.dealerlenium.model.ClientesSelecaoPagina
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException

class ContasReceberControllerTest {
    private val contas = mock(ContasReceberService::class.java)
    private val pix = mock(ContaReceberPixService::class.java)
    private val mvc = MockMvcBuilders.standaloneSetup(ContasReceberController(contas, pix))
        .setControllerAdvice(ApiExceptionHandler()).build()

    @Test
    fun `endpoint recebe filtros numericos separados e rejeita formato invalido`() {
        given(contas.listar(anyArg(), anyArg(), anyBoolean())).willReturn(ContasReceberPagina(emptyList(), 0))
        mvc.perform(get("/api/contas-receber").param("lancamento", "1107684").param("documento", "349412").param("parcela", "4")
            .principal(usuarioReceber("FILIAL_A"))).andExpect(status().isOk)
        val filtro = mockingDetails(contas).invocations.single().arguments[0] as br.andrew.dealerlenium.model.ContasReceberFiltro
        kotlin.test.assertEquals(1107684, filtro.lancamento)
        kotlin.test.assertEquals(349412, filtro.documento)
        kotlin.test.assertEquals(4, filtro.parcela)
        mvc.perform(get("/api/contas-receber").param("parcela", "4.5")).andExpect(status().isBadRequest)
    }

    @Test
    fun `endpoint clientes aceita busca sem empresaId nem datas`() {
        given(contas.listarClientes(anyArg(), anyArg(), anyBoolean())).willReturn(
            ClientesSelecaoPagina(listOf(ClienteSelecao("4", "Cielo", null)), 1),
        )
        mvc.perform(get("/api/contas-receber/clientes").param("busca", "12345678900").param("todos", "true").principal(usuarioReceber("FILIAL_A")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.itens[0].id").value("4"))
        val filtro = mockingDetails(contas).invocations.single().arguments[0] as br.andrew.dealerlenium.model.ContasReceberFiltro
        kotlin.test.assertNull(filtro.empresaId)
        kotlin.test.assertNull(filtro.vencimentoDe)
        kotlin.test.assertNull(filtro.vencimentoAte)
    }

    @Test
    fun `listagem expoe contrato consumido pelo front pix`() {
        given(contas.listar(anyArg(), anyArg(), anyBoolean())).willReturn(ContasReceberPagina(listOf(contaReceber()), 25))
        mvc.perform(get("/api/contas-receber").principal(usuarioReceber("FILIAL_A")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(25))
            .andExpect(jsonPath("$.itens[0].tituloId").value("123"))
            .andExpect(jsonPath("$.itens[0].empresaId").value("FILIAL_A"))
            .andExpect(jsonPath("$.itens[0].saldoAberto").value(50.0))
            .andExpect(jsonPath("$.itens[0].situacao").value("PARCIAL"))
    }

    @Test
    fun `todos e repassado para selecao completa sem limitar ao tamanho da pagina`() {
        val rows = (1..25).map { contaReceber().copy(tituloId = it.toString()) }
        given(contas.listar(anyArg(), anyArg(), anyBoolean())).willReturn(ContasReceberPagina(rows, 25))
        mvc.perform(get("/api/contas-receber").param("todos", "true").principal(usuarioReceber("FILIAL_A")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.itens.length()").value(25))
        kotlin.test.assertEquals(true, mockingDetails(contas).invocations.single().arguments[2])
    }

    @Test
    fun `rejeita data e situacao invalidas`() {
        mvc.perform(get("/api/contas-receber").param("vencimentoDe", "invalida")).andExpect(status().isBadRequest)
        mvc.perform(get("/api/contas-receber").param("situacao", "DESCONHECIDA")).andExpect(status().isBadRequest)
        verifyNoInteractions(contas)
    }

    @Test
    fun `body de cobranca exige filial e propaga falha de acesso`() {
        mvc.perform(post("/api/contas-receber/123/pix").contentType(MediaType.APPLICATION_JSON).content("""{"empresaId":""}"""))
            .andExpect(status().isBadRequest)
        verifyNoInteractions(pix)
        given(pix.gerar(anyInt(), anyArg(), anyArg())).willThrow(ResponseStatusException(HttpStatus.FORBIDDEN, "Filial nao permitida"))
        mvc.perform(post("/api/contas-receber/123/pix").principal(usuarioReceber("FILIAL_A"))
            .contentType(MediaType.APPLICATION_JSON).content("""{"empresaId":"FILIAL_B"}"""))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("Filial nao permitida"))
    }

    @Test
    fun `body ausente incompleto ou JSON invalido retorna 400`() {
        for (body in listOf("{}", "{", """{"empresaId":null}""")) {
            mvc.perform(post("/api/contas-receber/123/pix").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
        }
        verifyNoInteractions(pix)
    }
}
