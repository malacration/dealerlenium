package br.andrew.dealerlenium.infrastructure.configurations

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("dealer.web-service")
data class DealerWebServiceProperties(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val username: String = "",
    val password: String = "",
    // O WS não documenta paginação. Falhar explicitamente em vez de truncar o total.
    val maxTitulosPorEmpresa: Int = 10_000,
)
