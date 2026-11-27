package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.infrastructure.configurations.DealerWebServiceProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.util.HtmlUtils
import com.sun.net.httpserver.HttpServer
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Dados ficticios: codigos, documentos e valores nao correspondem a registros reais. */
class NotasEntradaContasPagarServiceTest {
    private val properties = DealerWebServiceProperties(true, "https://dealer.example/ws", "usuario-teste", "senha-teste")
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val service = NotasEntradaContasPagarService(DealerTituloWebService(properties, ObjectMapper(), builder, false), properties)

    private fun titulo(codigo: Int, nfCod: Long = 0, nfNumero: Long = 0, pessoa: Long = 50, doc: String = "98.765.432/0001-00",
                       valor: String = "100.00", empresaCod: Int = 7, movimento: String = "P", status: String = "AUT") = """
        {"Titulo_Codigo":$codigo,"Titulo_EmpresaCod":$empresaCod,"Titulo_MovimentoFinanceiro":"$movimento",
         "Titulo_Status":"$status","Titulo_PessoaCod":$pessoa,"Titulo_PessoaDocIdentificador":"$doc",
         "Titulo_PessoaNome":"Fornecedor X","Titulo_NotaFiscalCod":$nfCod,"Titulo_NotaFiscalNumero":$nfNumero,
         "Titulo_Valor":"$valor","Titulo_DataEmissao":"2026-11-03T00:00:00","Titulo_DataEntrada":"2026-11-05",
         "Titulo_DataVencimento":"2026-12-0${codigo % 9 + 1}"}
    """.trimIndent()

    private fun soap(vararg titulos: String) = """
        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body>
        <WS_GrupoROVEMA.TITULOResponse xmlns="DealerNet">
        <Json_dadosfiltro>${HtmlUtils.htmlEscape("""{"SDT_TituloSelecaoConsultarWS":[${titulos.joinToString(",")}]}""")}</Json_dadosfiltro>
        <Json_titulos>[]</Json_titulos><Outmensagem>Operação realizada com sucesso.</Outmensagem>
        </WS_GrupoROVEMA.TITULOResponse></soap:Body></soap:Envelope>
    """.trimIndent()

    private val periodo = ListagemNotasEntradaRequest(empresaCodigo = 7, dataInicial = "2026-11-01", dataFinal = "2026-11-30")

    @Test
    fun `agrupa parcelas da mesma nota e ignora cancelado, outra empresa e recebimento`() {
        server.expect(requestTo(properties.endpoint))
            .andExpect(content().string(containsString("\"Titulo_MovimentoFinanceiro\":\"P\"")))
            .andExpect(content().string(containsString("\"TituloTipoData\":\"ENT\"")))
            .andExpect(content().string(containsString("\"Titulo_EmpresaCod\":7")))
            .andRespond(withSuccess(soap(
                titulo(1, nfCod = 900, nfNumero = 123, valor = "60.00"),
                titulo(2, nfCod = 900, nfNumero = 123, valor = "40.10"),
                titulo(3, nfNumero = 456, pessoa = 51),
                titulo(4),
                titulo(5, nfCod = 901, nfNumero = 789, status = "CAN"),
                titulo(6, nfCod = 902, nfNumero = 790, empresaCod = 8),
                titulo(7, nfCod = 903, nfNumero = 791, movimento = "R"),
            ), MediaType.TEXT_XML))

        val notas = service.listar(periodo).associateBy { it.id }

        assertEquals(setOf("nf900", "p51-n456", "t4"), notas.keys)
        val nf = notas.getValue("nf900")
        assertEquals(123L, nf.numero)
        assertEquals(BigDecimal("100.10"), nf.valorTotal)
        assertEquals(listOf(1, 2), nf.parcelas.map { it.tituloCodigo })
        assertEquals("98765432000100", nf.cpfCnpjFornecedor)
        assertEquals("2026-11-03", nf.dataEmissao)
        assertEquals("2026-11-05", nf.dataLancamento)
        assertNull(nf.problema)
        assertEquals("Titulo sem nota fiscal vinculada no Dealer", notas.getValue("t4").problema)
        server.verify()
    }

    @Test
    fun `filtros de fornecedor e numero sao reaplicados no retorno`() {
        server.expect(requestTo(properties.endpoint)).andRespond(withSuccess(
            soap(titulo(1, nfCod = 900, nfNumero = 123), titulo(2, nfCod = 901, nfNumero = 124, doc = "11.111.111/0001-11")), MediaType.TEXT_XML))
        val notas = service.listar(periodo.copy(cpfCnpjFornecedor = "98765432000100", numeroNota = "0123"))
        assertEquals(listOf("nf900"), notas.map { it.id })
    }

    @Test
    fun `busca por id consulta a nota fiscal e devolve so a nota pedida`() {
        server.expect(requestTo(properties.endpoint))
            .andExpect(content().string(containsString("\"Titulo_NotaFiscalCod\":900")))
            .andRespond(withSuccess(soap(titulo(1, nfCod = 900, nfNumero = 123)), MediaType.TEXT_XML))
        assertEquals(listOf("nf900"), service.listar(ListagemNotasEntradaRequest(empresaCodigo = 7, ids = listOf("nf900"))).map { it.id })
        for (invalido in listOf("nf0", "x' OR 1=1", "nf99999999999999999999")) {
            assertFailsWith<ResponseStatusException> { service.listar(ListagemNotasEntradaRequest(empresaCodigo = 7, ids = listOf(invalido))) }
        }
    }

    @Test
    fun `parcelas com fornecedores diferentes nao sao importaveis`() {
        server.expect(requestTo(properties.endpoint)).andRespond(withSuccess(
            soap(titulo(1, nfCod = 900, nfNumero = 123), titulo(2, nfCod = 900, nfNumero = 123, doc = "11.111.111/0001-11")), MediaType.TEXT_XML))
        assertEquals("Parcelas da nota com fornecedores diferentes no Dealer", service.listar(periodo).single().problema)
    }

    @Test
    fun `valida empresa, periodo e filtros antes de chamar o WS`() {
        val invalidos = listOf(
            periodo.copy(empresaCodigo = null),
            periodo.copy(empresaCodigo = 0),
            periodo.copy(dataInicial = null),
            periodo.copy(dataFinal = "30/11/2026"),
            periodo.copy(dataInicial = "2026-12-01"),
            periodo.copy(dataInicial = "2026-10-01"),
            periodo.copy(cpfCnpjFornecedor = "123"),
            periodo.copy(numeroNota = "12a"),
            periodo.copy(ids = emptyList()),
            periodo.copy(ids = (1..101).map { "nf$it" }),
        )
        for (req in invalidos) {
            val erro = assertFailsWith<ResponseStatusException> { service.listar(req) }
            assertEquals(HttpStatus.BAD_REQUEST, erro.statusCode)
        }
        server.verify()
    }

    @Test
    fun `busca em lote para ao estourar o prazo total sem lista parcial`() {
        var agora = 0L
        service.relogio = { agora }
        server.expect(requestTo(properties.endpoint))
            .andExpect(content().string(containsString("\"Titulo_NotaFiscalCod\":900")))
            .andRespond { request ->
                agora += properties.prazoLote.toNanos() // a primeira chamada consumiu o prazo inteiro
                withSuccess(soap(titulo(1, nfCod = 900, nfNumero = 123)), MediaType.TEXT_XML).createResponse(request)
            }
        val erro = assertFailsWith<ResponseStatusException> {
            service.listar(ListagemNotasEntradaRequest(empresaCodigo = 7, ids = listOf("nf900", "nf901", "nf902")))
        }
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, erro.statusCode)
        server.verify() // so uma chamada ao WS: as demais nem foram feitas
    }

    @Test
    fun `WS travado estoura o read timeout e vira 504`() {
        val liberar = CountDownLatch(1)
        val ws = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/ws") { troca ->
                liberar.await(10, TimeUnit.SECONDS)
                runCatching { troca.sendResponseHeaders(500, -1) }
                troca.close()
            }
            start()
        }
        try {
            val lento = properties.copy(endpoint = "http://127.0.0.1:${ws.address.port}/ws",
                connectTimeout = Duration.ofSeconds(2), readTimeout = Duration.ofMillis(300))
            val comPrazos = NotasEntradaContasPagarService(DealerTituloWebService(lento, ObjectMapper(), RestClient.builder()), lento)
            val inicio = System.nanoTime()
            val erro = assertFailsWith<ResponseStatusException> { comPrazos.listar(periodo) }
            assertEquals(HttpStatus.GATEWAY_TIMEOUT, erro.statusCode)
            assertTrue(Duration.ofNanos(System.nanoTime() - inicio) < Duration.ofSeconds(5), "nao esperou o WS")
        } finally {
            liberar.countDown()
            ws.stop(0)
        }
    }
}
