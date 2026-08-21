package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.DealerProperties
import br.andrew.dealerlenium.TransactionMonitoringProperties
import br.andrew.dealerlenium.model.TipoTransacao
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionMonitoringPolicyTest {

    private val referencia = Instant.parse("2026-08-21T12:00:00Z")

    @Test
    fun `backoff dobra a cada falha consecutiva ate o teto`() {
        val policy = policy(jitter = Duration.ZERO, maxCheckInterval = Duration.ofMinutes(30))

        assertEquals(referencia.plus(Duration.ofMinutes(10)), proxima(policy, tentativas = 1))
        assertEquals(referencia.plus(Duration.ofMinutes(20)), proxima(policy, tentativas = 2))
        assertEquals(referencia.plus(Duration.ofMinutes(30)), proxima(policy, tentativas = 3))
        assertEquals(referencia.plus(Duration.ofMinutes(30)), proxima(policy, tentativas = 10))
    }

    @Test
    fun `jitter mantem o reagendamento dentro da janela configurada`() {
        val policy = policy(jitter = Duration.ofSeconds(30), maxCheckInterval = Duration.ofMinutes(30))

        repeat(50) {
            val proxima = policy.proximaExecucao(TipoTransacao.ADIANTAMENTO, referencia)
            assertTrue(!proxima.isBefore(referencia.plus(Duration.ofMinutes(5))))
            assertTrue(proxima.isBefore(referencia.plus(Duration.ofMinutes(5)).plusSeconds(30)))
        }
    }

    private fun proxima(policy: TransactionMonitoringPolicy, tentativas: Int) =
        policy.proximaExecucaoAposFalha(TipoTransacao.ADIANTAMENTO, tentativas, referencia)

    private fun policy(jitter: Duration, maxCheckInterval: Duration): TransactionMonitoringPolicy {
        val properties = DealerProperties(
            loginUrl = "http://localhost/login",
            indexUrl = "http://localhost/index",
            tipoTitulo = "PIX QR-CODE UZZIPAY",
            transactionMonitoring = TransactionMonitoringProperties(
                intervals = mapOf(TipoTransacao.ADIANTAMENTO to Duration.ofMinutes(5)),
                jitter = jitter,
                maxCheckInterval = maxCheckInterval,
            ),
        )
        return TransactionMonitoringPolicy(properties)
    }
}
