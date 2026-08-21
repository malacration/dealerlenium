package br.andrew.dealerlenium.infrastructure.configurations

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "uzzipix")
data class UzziPixProperties(
    val baseUrl: String,
    /** Janela em que a consulta de uma transacao em aberto e reaproveitada sem ir ao PSP. */
    val consultaTtl: Duration = Duration.ofSeconds(45),
    /** Transacao ja paga nao muda mais de estado, entao pode ser reaproveitada por muito mais tempo. */
    val consultaTtlPago: Duration = Duration.ofMinutes(30),
    /** Quanto tempo parar de chamar o PSP apos um 429 sem header Retry-After. */
    val bloqueioPadraoAposExcesso: Duration = Duration.ofSeconds(60),
    /** Teto para o Retry-After informado pelo PSP, para nao congelar o monitoramento. */
    val bloqueioMaximoAposExcesso: Duration = Duration.ofMinutes(5),
)
