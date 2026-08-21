package br.andrew.dealerlenium.schedule

import br.andrew.dealerlenium.DealerProperties
import br.andrew.dealerlenium.repositorys.TransactionRepository
import br.andrew.dealerlenium.service.OpenTransactionSettlementProcessor
import br.andrew.dealerlenium.service.TransactionMonitoringPolicy
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Service
class OpenTransactionSettlementScheduler(
    private val dealerProperties: DealerProperties,
    private val monitoringPolicy: TransactionMonitoringPolicy,
    private val transactionRepository: TransactionRepository,
    private val settlementProcessor: OpenTransactionSettlementProcessor,
    private val sleeper: Sleeper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "open-transaction-settlement-monitor").apply {
                isDaemon = true
            }
        }

    @PostConstruct
    fun start() {
        if (!monitoringPolicy.monitoramentoHabilitado()) {
            logger.info("Monitoramento de transacoes PIX desabilitado")
            return
        }

        val intervalo = dealerProperties.transactionMonitoring.pollInterval
        require(!intervalo.isNegative && !intervalo.isZero) {
            "dealer.transaction-monitoring.poll-interval precisa ser maior que zero"
        }

        executor.scheduleWithFixedDelay(
            ::processarTransacoesAbertas,
            intervalo.toMillis(),
            intervalo.toMillis(),
            TimeUnit.MILLISECONDS,
        )
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
        executor.awaitTermination(1, TimeUnit.MINUTES)
    }

    internal fun processarTransacoesAbertas() {
        val agora = Instant.now()
        val monitoring = dealerProperties.transactionMonitoring

        runCatching {
            val lote = transactionRepository.findOpenTransactionsReadyForMonitoring(
                agora,
                PageRequest.of(0, monitoring.batchSize, Sort.by(Sort.Direction.ASC, "proximaVerificacaoEm")),
            )

            for ((indice, transaction) in lote.withIndex()) {
                val transactionId = transaction.id
                if (transactionId == null) {
                    logger.warn("Transacao sem id ignorada: txId={}", transaction.txId)
                    continue
                }

                // Espacar as consultas evita a rajada que faz o PSP responder 429.
                if (indice > 0) {
                    sleeper.sleep(monitoring.spacing)
                }

                runCatching {
                    settlementProcessor.process(transactionId)
                }.onFailure { error ->
                    logger.error("Falha ao monitorar transacao {}", transactionId, error)
                }
            }
        }.onFailure { error ->
            if (error is InterruptedException) {
                Thread.currentThread().interrupt()
                logger.warn("Ciclo de monitoramento interrompido")
            } else {
                logger.error("Falha ao listar transacoes abertas para monitoramento", error)
            }
        }
    }
}
