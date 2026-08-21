package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.DealerProperties
import br.andrew.dealerlenium.model.TipoTransacao
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

@Component
class TransactionMonitoringPolicy(
    private val dealerProperties: DealerProperties,
) {
    fun monitoramentoHabilitado(): Boolean = dealerProperties.transactionMonitoring.enabled

    fun intervaloConsulta(tipoTransacao: TipoTransacao): Duration {
        val intervalo = dealerProperties.transactionMonitoring.intervals[tipoTransacao]
            ?: dealerProperties.transactionMonitoring.defaultCheckInterval

        require(!intervalo.isNegative && !intervalo.isZero) {
            "Intervalo de consulta invalido para $tipoTransacao: $intervalo"
        }

        return intervalo
    }

    fun proximaExecucao(tipoTransacao: TipoTransacao, referencia: Instant = Instant.now()): Instant {
        return referencia.plus(intervaloConsulta(tipoTransacao)).plusMillis(jitterMillis())
    }

    /**
     * Backoff exponencial depois de falhas consecutivas na consulta ao PSP.
     * Sem isso uma transacao que falha continua elegivel no ciclo seguinte e o backlog so cresce.
     */
    fun proximaExecucaoAposFalha(
        tipoTransacao: TipoTransacao,
        tentativas: Int,
        referencia: Instant = Instant.now(),
    ): Instant {
        val fator = 1L shl tentativas.coerceIn(0, MAX_EXPOENTE_BACKOFF)
        val intervalo = minOf(
            intervaloConsulta(tipoTransacao).multipliedBy(fator),
            dealerProperties.transactionMonitoring.maxCheckInterval,
        )
        return referencia.plus(intervalo).plusMillis(jitterMillis())
    }

    private fun jitterMillis(): Long {
        val jitter = dealerProperties.transactionMonitoring.jitter.toMillis()
        return if (jitter <= 0) 0 else Random.nextLong(jitter)
    }

    private companion object {
        const val MAX_EXPOENTE_BACKOFF = 4
    }
}

