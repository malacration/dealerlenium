package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.infrastructure.configurations.DealerWebServiceProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.ConnectException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.*

class DealerTituloWebServiceTest {
    private val properties = DealerWebServiceProperties(true, "https://dealer.example/ws", "user&name", "secret<password>")
    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val service = DealerTituloWebService(properties, ObjectMapper(), builder)

    @Test
    fun `consulta SOAP envia lancamento documento e parcela como filtros numericos`() {
        server.expect(requestTo(properties.endpoint))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"Titulo_Codigo\":1107684")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"Titulo_Numero\":349412")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"Titulo_Parcela\":4")))
            .andRespond(withSuccess(soap(), MediaType.TEXT_XML))
        service.consultar(1, tituloCodigo = 1107684, tituloNumero = 349412, tituloParcela = 4)
        server.verify()
    }

    @Test
    fun `consulta pessoa envia somente documento e credenciais e normaliza codigo`() {
        server.expect(requestTo(properties.endpoint))
            .andExpect(header("SOAPAction", "\"DealerNetaction/AWS_GRUPOROVEMA.CONSULTAPESSOA\""))
            .andExpect { request ->
                val xml = (request as org.springframework.mock.http.client.MockClientHttpRequest).bodyAsString
                assertContains(xml, "<Pessoa_documento>12345678900</Pessoa_documento>")
                assertContains(xml, "user&amp;name")
                assertContains(xml, "secret&lt;password&gt;")
                for (unwanted in listOf("Json_dadosfiltro", "TituloTipoData", "Data_De", "Data_Ate", "Titulo_EmpresaCod")) assertFalse(xml.contains(unwanted))
            }
            .andRespond(withSuccess(pessoaResponse("""<SDT_FSPessoaOutCollection xmlns="DealerNet"><SDT_FSPessoaOut><Pessoa_Codigo>4.00</Pessoa_Codigo><Pessoa_Nome>Cielo</Pessoa_Nome><Pessoa_DocIdentificador>123.456.789-00</Pessoa_DocIdentificador></SDT_FSPessoaOut></SDT_FSPessoaOutCollection>"""), MediaType.TEXT_XML))
        val pessoa = service.consultarPessoa("12345678900").single()
        assertEquals("4", pessoa.id)
        assertEquals("Cielo", pessoa.nome)
        assertEquals("12345678900", pessoa.documento)
        server.verify()
    }

    @Test
    fun `consulta pessoa trata nao encontrada e colecao vazia`() {
        assertTrue(service.parsePessoa(pessoaResponse("Pessoa não encontrada"), "12345678900").isEmpty())
        assertTrue(service.parsePessoa(pessoaResponse("""<SDT_FSPessoaOutCollection xmlns="DealerNet"/>"""), "12345678900").isEmpty())
    }

    @Test
    fun `consulta pessoa rejeita falha mensagem xml inseguro e documento divergente`() {
        val document = "12345678900"
        for (response in listOf(
            fault("soap:Server", "Falha ao consultar pessoa"),
            pessoaResponse("Senha invalida ${properties.password}"),
            pessoaResponse("<invalido>"),
            pessoaResponse("""<!DOCTYPE x [<!ENTITY ext SYSTEM "file:///etc/passwd">]><SDT_FSPessoaOutCollection xmlns="DealerNet">&ext;</SDT_FSPessoaOutCollection>"""),
            pessoaResponse("""<SDT_FSPessoaOutCollection xmlns="DealerNet"><SDT_FSPessoaOut><Pessoa_Codigo>4</Pessoa_Codigo><Pessoa_Nome>Cielo</Pessoa_Nome><Pessoa_DocIdentificador>99999999999</Pessoa_DocIdentificador></SDT_FSPessoaOut></SDT_FSPessoaOutCollection>"""),
        )) {
            val ex = assertFailsWith<ResponseStatusException> { service.parsePessoa(response, document) }
            assertEquals(HttpStatus.BAD_GATEWAY, ex.statusCode)
            assertFalse(ex.reason.orEmpty().contains(properties.password))
        }
    }

    @Test
    fun `consulta pessoa nao envia nome codigo ou documento incompleto ao WS`() {
        for (query in listOf("", "Cielo", "4", "123")) assertFailsWith<IllegalArgumentException> { service.consultarPessoa(query) }
        server.verify()
    }

    private fun pessoaResponse(value: String): String = """<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body><WS_GrupoROVEMA.CONSULTAPESSOAResponse xmlns="DealerNet"><Xml_pessoa><![CDATA[$value]]></Xml_pessoa></WS_GrupoROVEMA.CONSULTAPESSOAResponse></soap:Body></soap:Envelope>"""

    @Test
    fun `timeout indica 504 e conserva causa tecnica no log`() {
        server.expect(requestTo(properties.endpoint)).andRespond { _ ->
            throw ResourceAccessException("I/O ao consultar WS", SocketTimeoutException("Read timed out"))
        }
        val ex = assertFailsWith<ResponseStatusException> { service.consultar(1) }
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.statusCode)
        assertContains(ex.reason.orEmpty(), "Tempo esgotado")
        assertContains(ex.reason.orEmpty(), "Read timed out")
        val causes = generateSequence<Throwable>(ex) { it.cause }.map { it.message.orEmpty() }.toList()
        assertTrue(causes.any { "SocketTimeoutException" in it })
        assertNotNull(ex.cause)
    }

    @Test
    fun `DNS indisponivel nao e confundido com erro de autenticacao do WS`() {
        server.expect(requestTo(properties.endpoint)).andRespond { _ ->
            throw ResourceAccessException("I/O ao consultar WS", UnknownHostException("dealer.example"))
        }
        val ex = assertFailsWith<ResponseStatusException> { service.consultar(1) }
        assertEquals(HttpStatus.BAD_GATEWAY, ex.statusCode)
        assertContains(ex.reason.orEmpty(), "DNS")
        assertContains(ex.reason.orEmpty(), "UnknownHostException")
    }

    @Test
    fun `diagnostico diferencia conexao recusada e TLS sem expor credenciais`() {
        val diagnostics = DealerWebServiceDiagnostics(properties)
        val refused = diagnostics.communication(ResourceAccessException("WS", ConnectException("Connection refused")))
        assertContains(refused.reason.orEmpty(), "estabelecer conexao")
        val tls = diagnostics.communication(ResourceAccessException("WS", SSLHandshakeException("certificate invalid")))
        assertContains(tls.reason.orEmpty(), "TLS/SSL")
        val privateError = diagnostics.communication(ResourceAccessException(
            "Falhou usuario=${properties.username} senha=${properties.password}",
            java.io.IOException("Request failed: ${properties.password}"),
        ))
        for (cause in generateSequence<Throwable>(privateError) { it.cause }) {
            assertFalse(cause.message.orEmpty().contains(properties.password))
            assertFalse(cause.message.orEmpty().contains(properties.username))
        }
    }

    @Test
    fun `mostra status HTTP real e motivo de SOAP Fault retornado pelo servidor`() {
        server.expect(requestTo(properties.endpoint)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.TEXT_XML).body(fault("soap:Server", "Consulta excedeu o tempo permitido no servidor.")))
        val ex = assertFailsWith<ResponseStatusException> { service.consultar(1) }
        assertEquals(HttpStatus.BAD_GATEWAY, ex.statusCode)
        assertContains(ex.reason.orEmpty(), "HTTP 500")
        assertContains(ex.reason.orEmpty(), "soap:Server")
        assertContains(ex.reason.orEmpty(), "Consulta excedeu o tempo permitido")
    }

    @Test
    fun `HTTP de erro sem corpo SOAP ainda informa o status sem despejar HTML`() {
        server.expect(requestTo(properties.endpoint)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
            .contentType(MediaType.TEXT_HTML).body("<html><body>payload-privado</body></html>"))
        val ex = assertFailsWith<ResponseStatusException> { service.consultar(1) }
        assertContains(ex.reason.orEmpty(), "HTTP 503")
        assertFalse(ex.reason.orEmpty().contains("payload-privado"))
    }

    @Test
    fun `SOAP Fault em HTTP 200 tambem conserva motivo informado pelo WS`() {
        val ex = assertFailsWith<ResponseStatusException> { service.parse(fault("soap:Client", "Filial nao habilitada no WS.")) }
        assertContains(ex.reason.orEmpty(), "Filial nao habilitada")
    }

    @Test
    fun `mensagem do WS e devolvida sanitizada`() {
        val ex = assertFailsWith<ResponseStatusException> { service.parse(soap(message = "Acesso negado: usuario=${properties.username}")) }
        assertContains(ex.reason.orEmpty(), "Acesso negado")
        assertFalse(ex.reason.orEmpty().contains(properties.username))
        assertContains(ex.reason.orEmpty(), "[redigido]")
    }

    @Test
    fun `cliente escolhido gera filtro de codigo da pessoa na consulta SOAP`() {
        server.expect(requestTo(properties.endpoint))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"Titulo_PessoaCod\":4")))
            .andRespond(withSuccess(soap(), MediaType.TEXT_XML))
        service.consultar(1, pessoaCodigo = 4)
        server.verify()
    }

    @Test
    fun `consulta envia somente CON com credenciais escapadas e filtros reais do WSDL`() {
        server.expect(requestTo(properties.endpoint))
            .andExpect(header("SOAPAction", "\"DealerNetaction/AWS_GRUPOROVEMA.TITULO\""))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("<Modo>CON</Modo>")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("user&amp;name")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("secret&lt;password&gt;")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"Titulo_EmpresaCod\":1")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"Data_De\":\"2026-10-20\"")))
            .andRespond(withSuccess(soap(nested = """{"SDT_TituloSelecaoConsultarWS":[${tituloWs()}]}"""), MediaType.TEXT_XML))
        val rows = service.consultar(1, LocalDate.of(2026, 10, 20))
        assertEquals(1, rows.size)
        assertEquals(123, rows[0]["Titulo_Codigo"].asInt())
        server.verify()
    }

    @Test
    fun `le tambem o formato Json_titulos documentado no PDF`() {
        assertEquals(1, service.parse(soap(titles = "[${tituloWs()}]")).size)
        assertTrue(service.parse(soap(nested = """{"SDT_TituloSelecaoConsultarWS":[]}""")).isEmpty())
    }

    @Test
    fun `recusa falha de autenticacao em vez de responder lista vazia`() {
        val ex = assertFailsWith<ResponseStatusException> { service.parse(soap(message = "Usuario ou senha invalidos")) }
        assertEquals(HttpStatus.BAD_GATEWAY, ex.statusCode)
        assertFalse(ex.reason.orEmpty().contains(properties.password))
    }

    @Test
    fun `recusa JSON invalido e SOAP Fault`() {
        assertFailsWith<ResponseStatusException> { service.parse(soap(titles = "{}")) }
        assertFailsWith<ResponseStatusException> { service.parse("""<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><s:Fault/></s:Body></s:Envelope>""") }
    }

    @Test
    fun `bloqueia entidades externas e nao le arquivos locais`() {
        val xml = """<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><foo>&xxe;</foo>"""
        val ex = assertFailsWith<ResponseStatusException> { service.parse(xml) }
        assertFalse(ex.reason.orEmpty().contains("root:"))
    }

    @Test
    fun `limite de registros falha sem retornar total truncado`() {
        val limited = DealerTituloWebService(properties.copy(maxTitulosPorEmpresa = 1), ObjectMapper(), RestClient.builder())
        assertFailsWith<ResponseStatusException> { limited.parse(soap(titles = "[${tituloWs()},${tituloWs(124)}]")) }
    }

    private fun soap(titles: String = "[]", nested: String = "{}", message: String = "") = """
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>
        <WS_GrupoROVEMA.TITULOResponse xmlns="DealerNet">
          <Json_dadosfiltro><![CDATA[$nested]]></Json_dadosfiltro>
          <Json_titulos><![CDATA[$titles]]></Json_titulos>
          <Outmensagem><![CDATA[$message]]></Outmensagem>
        </WS_GrupoROVEMA.TITULOResponse></s:Body></s:Envelope>
    """.trimIndent()

    private fun fault(code: String, reason: String) = """
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>
        <s:Fault><faultcode>$code</faultcode><faultstring>$reason</faultstring></s:Fault>
        </s:Body></s:Envelope>
    """.trimIndent()
}
