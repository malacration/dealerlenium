package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.handdle.TransactionSettlementDispatcher
import br.andrew.dealerlenium.model.PixTransactionConsultationResponse
import br.andrew.dealerlenium.model.TransactionDocument
import br.andrew.dealerlenium.repositorys.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class OpenTransactionSettlementProcessor(
    private val transactionRepository: TransactionRepository,
    private val pixPagamentoService: PixPagamentoService,
    private val monitoringPolicy: TransactionMonitoringPolicy,
    private val settlementDispatcher: TransactionSettlementDispatcher,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val transactionLocks = ConcurrentHashMap<String, ReentrantLock>()

    fun process(transactionId: String): PixTransactionConsultationResponse {
        val lock = transactionLocks.computeIfAbsent(transactionId) { ReentrantLock() }
        return try {
            lock.withLock {
                processLocked(transactionId)
            }
        } finally {
            if (!lock.isLocked && !lock.hasQueuedThreads()) {
                transactionLocks.remove(transactionId, lock)
            }
        }
    }

    private fun processLocked(transactionId: String): PixTransactionConsultationResponse {
        val transaction = transactionRepository.findById(transactionId).orElseThrow {
            IllegalArgumentException("Transacao $transactionId nao encontrada")
        }
        val agora = Instant.now()
        val pagamento = pixPagamentoService.consultarPagamentoDaTransacao(transactionId)

        if (transaction.baixaRealizadaEm != null) {
            return pagamento
        }

        return if (pagamento.paid) {
            processarPagamentoConfirmado(transaction, pagamento, agora)
        } else {
            processarPagamentoPendente(transaction, pagamento, agora)
        }
    }

    private fun processarPagamentoConfirmado(
        transaction: TransactionDocument,
        pagamento: PixTransactionConsultationResponse,
        agora: Instant,
    ): PixTransactionConsultationResponse {
        return runCatching {
            settlementDispatcher.baixa(transaction, pagamento)
            transactionRepository.save(
                transaction.copy(
                    status = pagamento.status ?: "Pago",
                    ultimaVerificacaoEm = agora,
                    pagamentoConfirmadoEm = parsePaymentDate(pagamento.paymentDate) ?: agora,
                    baixaRealizadaEm = agora,
                    encerradaEm = agora,
                    proximaVerificacaoEm = null,
                    ultimaFalhaProcessamento = null,
                ),
            )
            pagamento
        }.getOrElse { error ->
            logger.error("Falha ao realizar baixa da transacao {}", transaction.id ?: transaction.txId, error)
            transactionRepository.save(
                transaction.copy(
                    status = pagamento.status ?: "Pago",
                    ultimaVerificacaoEm = agora,
                    pagamentoConfirmadoEm = parsePaymentDate(pagamento.paymentDate) ?: agora,
                    proximaVerificacaoEm = monitoringPolicy.proximaExecucao(transaction.tipoTransacao, agora),
                    ultimaFalhaProcessamento = error.message ?: error::class.simpleName,
                ),
            )
            throw error
        }
    }

    private fun processarPagamentoPendente(
        transaction: TransactionDocument,
        pagamento: PixTransactionConsultationResponse,
        agora: Instant,
    ): PixTransactionConsultationResponse {
        val expirado = !transaction.pixExpiraEm.isAfter(agora)

        transactionRepository.save(
            transaction.copy(
                status = pagamento.status ?: if (expirado) "Expirado" else transaction.status,
                ultimaVerificacaoEm = agora,
                proximaVerificacaoEm = if (expirado) null else monitoringPolicy.proximaExecucao(transaction.tipoTransacao, agora),
                encerradaEm = if (expirado) agora else null,
                ultimaFalhaProcessamento = null,
            ),
        )

        return pagamento
    }

    private fun parsePaymentDate(value: String?): Instant? {
        if (value.isNullOrBlank()) {
            return null
        }

        return runCatching { Instant.parse(value) }
            .recoverCatching { OffsetDateTime.parse(value).toInstant() }
            .recoverCatching { OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toInstant() }
            .getOrNull()
    }
}
