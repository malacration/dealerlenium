package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.infrastructure.configurations.DealerWebServiceProperties
import br.andrew.dealerlenium.model.ClienteSelecao
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException
import org.w3c.dom.Element
import org.w3c.dom.Document
import java.io.StringReader
import java.io.StringWriter
import java.time.LocalDate
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.stream.XMLOutputFactory
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

@Service
class DealerTituloWebService(
    private val properties: DealerWebServiceProperties,
    private val mapper: ObjectMapper,
    builder: RestClient.Builder,
) {
    private val client = builder.build()
    private val diagnostics = DealerWebServiceDiagnostics(properties)

    fun consultar(
        empresaCodigo: Int,
        vencimentoDe: LocalDate? = null,
        vencimentoAte: LocalDate? = null,
        tituloCodigo: Int? = null,
        pessoaDocumento: String? = null,
        pessoaCodigo: Int? = null,
        tituloNumero: Int? = null,
        tituloParcela: Int? = null,
    ): List<JsonNode> {
        val filtros = linkedMapOf<String, Any>(
            "Titulo_EmpresaCod" to empresaCodigo,
            "Titulo_MovimentoFinanceiro" to "R",
            "TituloTipoData" to "VEN",
            "Data_De" to (vencimentoDe ?: LocalDate.of(1900, 1, 1)).toString(),
            "Data_Ate" to (vencimentoAte ?: LocalDate.of(9999, 12, 31)).toString(),
        )
        tituloCodigo?.let { filtros["Titulo_Codigo"] = it }
        tituloNumero?.let { filtros["Titulo_Numero"] = it }
        tituloParcela?.let { filtros["Titulo_Parcela"] = it }
        pessoaDocumento?.let { filtros["Pessoa_DocIdentificador"] = it }
        pessoaCodigo?.let { filtros["Titulo_PessoaCod"] = it }
        return parse(request("TITULO", linkedMapOf(
            "Modo" to "CON", "Usuario" to properties.username, "Senha" to properties.password,
            "Json_dadosfiltro" to mapper.writeValueAsString(filtros), "Json_titulos" to "[]",
        )))
    }

    fun consultarPessoa(documento: String): List<ClienteSelecao> {
        require(documento.matches(Regex("[0-9]{11}|[0-9]{14}"))) { "Informe o CPF ou CNPJ completo do cliente." }
        return parsePessoa(request("CONSULTAPESSOA", linkedMapOf(
            "Usuario" to properties.username, "Senha" to properties.password, "Pessoa_documento" to documento,
        )), documento)
    }

    private fun request(operation: String, fields: Map<String, String>): String {
        if (!properties.enabled) throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Web Service Dealer nao habilitado.")
        if (properties.endpoint.isBlank() || properties.username.isBlank() || properties.password.isBlank()) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Configure endpoint e credenciais de dealer.web-service.")
        }
        return try {
            client.post().uri(properties.endpoint)
                .contentType(MediaType.parseMediaType("text/xml;charset=UTF-8"))
                .header("SOAPAction", "\"DealerNetaction/AWS_GRUPOROVEMA.$operation\"")
                .body(envelope(operation, fields))
                .retrieve()
                .onStatus({ it.isError }) { _, response ->
                    val status = response.statusCode.value()
                    val detail = runCatching {
                        val body = response.body.readNBytes(65_536).toString(Charsets.UTF_8)
                        val document = parseXml(body)
                        failureDetail(document) ?: text(document, "Outmensagem")?.let(diagnostics::safe)
                    }.getOrNull()?.takeIf { it.isNotEmpty() }
                    falha("Web Service Dealer respondeu HTTP $status${detail?.let { ": $it" } ?: "."}")
                }
                .body(String::class.java)
                ?: falha("Web Service Dealer retornou resposta vazia.")
        } catch (ex: RestClientException) {
            throw diagnostics.communication(ex)
        }
    }

    internal fun parsePessoa(response: String, documento: String): List<ClienteSelecao> {
        try {
            val document = parseXml(response)
            failureDetail(document)?.let { falha("Web Service de pessoas retornou SOAP Fault: $it") }
            val result = document.getElementsByTagNameNS("DealerNet", "WS_GrupoROVEMA.CONSULTAPESSOAResponse").item(0) as? Element
                ?: falha("Resposta SOAP de pessoas invalida.")
            val value = result.getElementsByTagNameNS("DealerNet", "Xml_pessoa").item(0)?.textContent?.trim()
                ?: falha("Resposta de pessoas sem Xml_pessoa.")
            if (value.equals("Pessoa não encontrada", ignoreCase = true) || value.equals("Pessoa nao encontrada", ignoreCase = true)) return emptyList()
            if (!value.startsWith("<")) falha("Web Service de pessoas recusou a consulta: ${diagnostics.safe(value)}")
            val pessoas = parseXml(value)
            val rows = pessoas.getElementsByTagNameNS("DealerNet", "SDT_FSPessoaOut")
            if (pessoas.documentElement.localName != "SDT_FSPessoaOutCollection" && rows.length == 0) {
                falha("XML de pessoas em formato invalido.")
            }
            return (0 until rows.length).map { index ->
                val row = rows.item(index) as Element
                fun field(name: String) = row.getElementsByTagNameNS("DealerNet", name).item(0)?.textContent?.trim().orEmpty()
                val id = field("Pessoa_Codigo").toBigDecimalOrNull()?.let { runCatching { it.intValueExact() }.getOrNull() }
                    ?.takeIf { it > 0 } ?: falha("Pessoa retornada com codigo invalido.")
                val nome = field("Pessoa_Nome").takeIf { it.isNotBlank() } ?: falha("Pessoa retornada sem nome.")
                val doc = field("Pessoa_DocIdentificador").filter(Char::isDigit)
                if (doc != documento) falha("Web Service retornou pessoa com documento diferente do consultado.")
                ClienteSelecao(id.toString(), nome, doc)
            }.distinctBy { it.id }
        } catch (ex: ResponseStatusException) {
            throw ex
        } catch (ex: Exception) {
            falha("Nao foi possivel interpretar a resposta do Web Service de pessoas.")
        }
    }

    private fun envelope(operation: String, fields: Map<String, String>): String {
        val output = StringWriter()
        val xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output)
        xml.writeStartDocument("UTF-8", "1.0")
        xml.writeStartElement("soap", "Envelope", SOAP_NAMESPACE)
        xml.writeNamespace("soap", SOAP_NAMESPACE)
        xml.writeStartElement("soap", "Body", SOAP_NAMESPACE)
        xml.writeStartElement("WS_GrupoROVEMA.$operation")
        xml.writeDefaultNamespace("DealerNet")
        for ((name, value) in fields) {
            xml.writeStartElement(name)
            xml.writeCharacters(value)
            xml.writeEndElement()
        }
        repeat(3) { xml.writeEndElement() }
        xml.writeEndDocument()
        xml.close()
        return output.toString()
    }

    internal fun parse(response: String): List<JsonNode> {
        try {
            val document = parseXml(response)
            failureDetail(document)?.let { falha("Web Service de titulos retornou SOAP Fault: $it") }
            val result = document.getElementsByTagNameNS("DealerNet", "WS_GrupoROVEMA.TITULOResponse").item(0) as? Element
                ?: falha("Resposta SOAP de titulos invalida.")
            val children = (0 until result.childNodes.length).map { result.childNodes.item(it) }
                .filterIsInstance<Element>().associate { it.localName to it.textContent }
            val wsMessage = children["Outmensagem"].orEmpty().trim()
            val message = wsMessage.lowercase()
            if (listOf("erro", "falha", "senha", "inválid", "invalid", "negado", "autentic").any { it in message }) {
                falha("Web Service recusou a consulta de titulos: ${diagnostics.safe(wsMessage)}")
            }
            // Homologação retorna a seleção no JSON de filtro. O PDF exemplifica Json_titulos.
            val filterJson = children["Json_dadosfiltro"]?.takeIf { it.isNotBlank() }?.let { mapper.readTree(it) }
            val nested = filterJson?.get("SDT_TituloSelecaoConsultarWS")
            val json = nested ?: children["Json_titulos"]?.takeIf { it.isNotBlank() }?.let { mapper.readTree(it) }
            if (json == null || !json.isArray) falha("Json_titulos deve conter uma lista JSON.")
            if (properties.maxTitulosPorEmpresa <= 0 || json.size() > properties.maxTitulosPorEmpresa) {
                falha("Consulta excedeu o limite de titulos por empresa. Restrinja o periodo de vencimento.")
            }
            return json.toList().onEach { row ->
                if (!row.isObject) falha("Titulo retornado em formato invalido.")
                val messages = row.get("Messages")
                val error = messages?.takeIf { it.isArray }?.firstOrNull { it.path("Type").asText().uppercase() in setOf("E", "ERROR", "ERRO") }
                if (error != null) {
                    val description = diagnostics.safe(error.path("Description").asText()).takeIf { it.isNotEmpty() }
                    falha("Web Service retornou erro no titulo${description?.let { ": $it" } ?: "."}")
                }
            }
        } catch (ex: ResponseStatusException) {
            throw ex
        } catch (ex: Exception) {
            falha("Nao foi possivel interpretar a resposta do Web Service de titulos.")
        }
    }

    private fun parseXml(response: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val builder = factory.newDocumentBuilder()
        builder.setErrorHandler(object : DefaultHandler() {
            override fun error(error: SAXParseException) { throw error }
            override fun fatalError(error: SAXParseException) { throw error }
        })
        return builder.parse(InputSource(StringReader(response)))
    }

    private fun text(document: Document, name: String): String? = document.getElementsByTagNameNS("*", name)
        .item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun failureDetail(document: Document): String? {
        if (document.getElementsByTagNameNS(SOAP_NAMESPACE, "Fault").length == 0) return null
        val code = text(document, "faultcode")
        val reason = text(document, "faultstring")
        return diagnostics.safe(listOfNotNull(code, reason).joinToString(": ")).ifEmpty { "Sem descricao fornecida pelo WS." }
    }

    private fun falha(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_GATEWAY, message)

    companion object {
        private const val SOAP_NAMESPACE = "http://schemas.xmlsoap.org/soap/envelope/"
    }
}
