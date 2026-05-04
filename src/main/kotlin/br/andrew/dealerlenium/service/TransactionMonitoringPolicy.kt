package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.DealerProperties
import br.andrew.dealerlenium.model.TipoTransacao
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

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
        return referencia.plus(intervaloConsulta(tipoTransacao))
    }
}

