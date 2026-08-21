package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.handdle.TransactionSettlementDispatcher
import br.andrew.dealerlenium.model.PixTransactionConsultationResponse
import br.andrew.dealerlenium.model.TransactionDocument
import br.andrew.dealerlenium.model.TransactionStatus
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

    fun retrySettlement(transactionId: String): PixTransactionConsultationResponse {
        val lock = transactionLocks.computeIfAbsent(transactionId) { ReentrantLock() }
        return try {
            lock.withLock {
                retrySettlementLocked(transactionId)
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
        if (transaction.status != TransactionStatus.CRIADO) {
            return pixPagamentoService.consultarEstadoLocal(transaction)
        }
        val agora = Instant.now()
        val pagamento = try {
            pixPagamentoService.consultarPagamentoDaTransacao(transaction)
        } catch (error: Throwable) {
            registrarFalhaDeConsulta(transaction, agora, error)
            throw error
        }

        if (pagamento.consultaDesatualizada) {
            // Dado veio do cache porque o PSP recusou a consulta: nao da para transicionar
            // estado (nem disparar baixa) com base nele.
            logger.warn("Consulta desatualizada do PSP para a transacao {}; estado nao sera alterado", transactionId)
            registrarFalhaDeConsulta(transaction, agora, null)
            return pagamento
        }

        return if (pagamento.paid) {
            processarPagamentoConfirmado(transaction, pagamento, agora)
        } else {
            processarPagamentoPendente(transaction, pagamento, agora)
        }
    }

    /**
     * Reagenda a transacao com backoff quando a consulta ao PSP falha.
     * Sem isso ela continua elegivel no ciclo seguinte e o backlog cresce a cada erro.
     */
    private fun registrarFalhaDeConsulta(
        transaction: TransactionDocument,
        agora: Instant,
        error: Throwable?,
    ) {
        val tentativas = transaction.tentativasConsulta + 1
        runCatching {
            transactionRepository.save(
                transaction.copy(
                    ultimaVerificacaoEm = agora,
                    tentativasConsulta = tentativas,
                    proximaVerificacaoEm = monitoringPolicy.proximaExecucaoAposFalha(
                        transaction.tipoTransacao,
                        tentativas,
                        agora,
                    ),
                    ultimaFalhaProcessamento = error?.let { resolveFailureMessage(it) }
                        ?: "Consulta ao PSP indisponivel; status desatualizado",
                ),
            )
        }.onFailure { falha ->
            logger.error("Falha ao reagendar a transacao {}", transaction.id ?: transaction.txId, falha)
        }
    }

    private fun retrySettlementLocked(transactionId: String): PixTransactionConsultationResponse {
        val transaction = transactionRepository.findById(transactionId).orElseThrow {
            IllegalArgumentException("Transacao $transactionId nao encontrada")
        }
        require(transaction.status == TransactionStatus.ERRO_BAIXA) {
            "A transacao $transactionId esta com status ${transaction.status.value}; somente transacoes com status ${TransactionStatus.ERRO_BAIXA.value} podem ter baixa retentada."
        }

        val agora = Instant.now()
        val pagamento = pixPagamentoService.consultarPagamentoDaTransacao(transaction, ignorarCache = true)
        check(!pagamento.consultaDesatualizada) {
            "Nao foi possivel confirmar o pagamento da transacao $transactionId com o banco no momento. Tente novamente em instantes."
        }
        require(pagamento.paid) {
            "A transacao $transactionId nao possui pagamento confirmado para retentar a baixa."
        }

        return processarPagamentoConfirmado(transaction, pagamento, agora)
    }

    private fun processarPagamentoConfirmado(
        transaction: TransactionDocument,
        pagamento: PixTransactionConsultationResponse,
        agora: Instant,
    ): PixTransactionConsultationResponse {
        return runCatching {
            val settlementResult = settlementDispatcher.baixa(transaction, pagamento)
            transactionRepository.save(
                transaction.copy(
                    status = TransactionStatus.PAGO,
                    ultimaVerificacaoEm = agora,
                    pagamentoConfirmadoEm = parsePaymentDate(pagamento.paymentDate) ?: agora,
                    baixaRealizadaEm = agora,
                    idBaixa = settlementResult.settlementId,
                    encerradaEm = agora,
                    proximaVerificacaoEm = null,
                    ultimaFalhaProcessamento = null,
                    tentativasConsulta = 0,
                ),
            )
            pagamento
        }.getOrElse { error ->
            logger.error("Falha ao realizar baixa da transacao {}", transaction.id ?: transaction.txId, error)
            transactionRepository.save(
                transaction.copy(
                    status = TransactionStatus.ERRO_BAIXA,
                    ultimaVerificacaoEm = agora,
                    pagamentoConfirmadoEm = parsePaymentDate(pagamento.paymentDate) ?: agora,
                    baixaRealizadaEm = null,
                    proximaVerificacaoEm = null,
                    encerradaEm = agora,
                    ultimaFalhaProcessamento = resolveFailureMessage(error),
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
                status = if (expirado) TransactionStatus.EXPIRADO else transaction.status,
                ultimaVerificacaoEm = agora,
                proximaVerificacaoEm = if (expirado) null else monitoringPolicy.proximaExecucao(transaction.tipoTransacao, agora),
                encerradaEm = if (expirado) agora else null,
                ultimaFalhaProcessamento = null,
                tentativasConsulta = 0,
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

    private fun resolveFailureMessage(error: Throwable): String {
        val message = error.message?.trim().orEmpty()
        return if (message.isNotEmpty()) {
            message
        } else {
            error::class.simpleName ?: "Falha desconhecida ao realizar baixa"
        }
    }
}
