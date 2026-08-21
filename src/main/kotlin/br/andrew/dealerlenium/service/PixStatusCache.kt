package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.infrastructure.configurations.UzziPixProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Cache de curta duracao das consultas de status no PSP.
 *
 * Resolve tres problemas: refresh repetido da tela vira uma unica chamada ao banco,
 * chamadas concorrentes para a mesma transacao sao agrupadas (single-flight) e,
 * quando o PSP recusa a consulta, o ultimo status conhecido e devolvido no lugar de um erro.
 */
@Component
class PixStatusCache(
    private val properties: UzziPixProperties,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val entradas = ConcurrentHashMap<String, Entrada>()
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    fun obter(
        chave: String,
        ignorarCache: Boolean = false,
        consulta: () -> UzziPixTransactionStatus,
    ): UzziPixTransactionStatus {
        if (!ignorarCache) {
            entradaValida(chave)?.let { return it.comoResposta(desatualizado = false) }
        }

        val lock = locks.computeIfAbsent(chave) { ReentrantLock() }
        return try {
            lock.withLock {
                if (!ignorarCache) {
                    entradaValida(chave)?.let { return@withLock it.comoResposta(desatualizado = false) }
                }
                consultarEArmazenar(chave, consulta)
            }
        } finally {
            if (!lock.isLocked && !lock.hasQueuedThreads()) {
                locks.remove(chave, lock)
            }
        }
    }

    private fun consultarEArmazenar(
        chave: String,
        consulta: () -> UzziPixTransactionStatus,
    ): UzziPixTransactionStatus {
        return try {
            val entrada = Entrada(consulta(), clock.instant())
            entradas[chave] = entrada
            descartarAntigas()
            entrada.comoResposta(desatualizado = false)
        } catch (error: Throwable) {
            val ultimaConhecida = entradas[chave] ?: throw error
            logger.warn(
                "Falha ao consultar {} no PSP; devolvendo o ultimo status conhecido de {}",
                chave,
                ultimaConhecida.consultadoEm,
                error,
            )
            ultimaConhecida.comoResposta(desatualizado = true)
        }
    }

    /** Evita crescimento indefinido do mapa em execucoes longas. */
    private fun descartarAntigas() {
        if (entradas.size <= MAXIMO_ENTRADAS) {
            return
        }
        val limite = clock.instant().minus(properties.consultaTtlPago)
        entradas.entries.removeIf { it.value.consultadoEm.isBefore(limite) }
    }

    private fun entradaValida(chave: String): Entrada? {
        val entrada = entradas[chave] ?: return null
        val ttl = if (entrada.status.resolvedPaid() == true) properties.consultaTtlPago else properties.consultaTtl
        return entrada.takeIf { clock.instant().isBefore(it.consultadoEm.plus(ttl)) }
    }

    private companion object {
        const val MAXIMO_ENTRADAS = 1_000
    }

    private data class Entrada(
        val status: UzziPixTransactionStatus,
        val consultadoEm: Instant,
    ) {
        fun comoResposta(desatualizado: Boolean): UzziPixTransactionStatus =
            status.copy(consultadoEm = consultadoEm, desatualizado = desatualizado)
    }
}
