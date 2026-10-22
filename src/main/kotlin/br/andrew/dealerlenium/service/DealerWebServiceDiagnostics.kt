package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.infrastructure.configurations.DealerWebServiceProperties
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException
import java.io.EOFException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException
import java.nio.channels.UnresolvedAddressException
import javax.net.ssl.SSLException

internal class DealerWebServiceDiagnostics(private val properties: DealerWebServiceProperties) {
    fun communication(error: RestClientException): ResponseStatusException {
        val chain = generateSequence<Throwable>(error) { it.cause }.take(12).toList()
        val timeout = chain.any { it is HttpTimeoutException || it is SocketTimeoutException }
        val reason = when {
            chain.any { it is HttpConnectTimeoutException } -> "Tempo esgotado ao conectar ao Web Service Dealer."
            timeout -> "Tempo esgotado ao aguardar resposta do Web Service Dealer."
            chain.any { it is UnknownHostException || it is UnresolvedAddressException } -> "Nao foi possivel resolver o endereco do Web Service Dealer (DNS)."
            chain.any { it is SSLException } -> "Falha de TLS/SSL na conexao com o Web Service Dealer."
            chain.any { it is NoRouteToHostException } -> "Nao existe rota de rede para o Web Service Dealer."
            chain.any { it is ConnectException } -> "Nao foi possivel estabelecer conexao com o Web Service Dealer."
            chain.any { it is EOFException } -> "O Web Service Dealer encerrou a conexao antes de completar a resposta."
            else -> "Falha de comunicacao com o Web Service Dealer."
        }
        val root = chain.last()
        val detail = safe(root.message.orEmpty())
        val message = if (detail.isEmpty()) "$reason Causa: ${root.javaClass.simpleName}." else "$reason Causa: ${root.javaClass.simpleName}: $detail"
        // O advice imprime a cadeia. Copiar mensagens sanitizadas mantém os frames
        // originais e a identificação dos tipos sem despejar credenciais no log.
        val cause = chain.asReversed().fold(null as Throwable?) { inner, original ->
            DealerWebServiceCause("${original.javaClass.name}: ${safe(original.message.orEmpty())}", inner)
                .apply { stackTrace = original.stackTrace }
        }
        return ResponseStatusException(if (timeout) HttpStatus.GATEWAY_TIMEOUT else HttpStatus.BAD_GATEWAY, message, cause)
    }

    fun safe(raw: String): String {
        var result = raw
        for (credential in listOf(properties.username, properties.password).filter { it.isNotBlank() }) {
            val escaped = credential.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;")
            for (value in setOf(credential, escaped)) result = result.replace(value, "[redigido]")
        }
        result = result.replace(Regex("(?i)(https?://)[^\\s/]*@"), "$1[redigido]@")
        result = result.replace(Regex("(?i)(senha|password|usuario_senha|authorization|token)\\s*[=:]\\s*[^\\s<>&;]+"), "$1=[redigido]")
        return result.replace(Regex("\\s+"), " ").trim().take(500)
    }
}

private class DealerWebServiceCause(message: String, cause: Throwable?) : RuntimeException(message, cause)
