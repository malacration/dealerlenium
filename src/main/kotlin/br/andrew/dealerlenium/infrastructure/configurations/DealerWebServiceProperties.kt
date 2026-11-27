package br.andrew.dealerlenium.infrastructure.configurations

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("dealer.web-service")
data class DealerWebServiceProperties(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val username: String = "",
    val password: String = "",
    // O WS não documenta paginação. Falhar explicitamente em vez de truncar o total.
    val maxTitulosPorEmpresa: Int = 10_000,
    // Prazos de cada chamada ao WS: servidor travado nao prende a thread indefinidamente.
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(30),
    // Prazo total de uma busca em lote (varias chamadas): abaixo do read-timeout do Nota Agil (120s),
    // somado a uma ultima chamada em andamento (read-timeout).
    val prazoLote: Duration = Duration.ofSeconds(80),
)
