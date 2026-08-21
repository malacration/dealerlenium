package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.infrastructure.configurations.UzziPixProperties
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PixStatusCacheTest {

    private val properties = UzziPixProperties(
        baseUrl = "http://localhost:8552",
        consultaTtl = Duration.ofSeconds(45),
        consultaTtlPago = Duration.ofMinutes(30),
    )

    @Test
    fun `reaproveita a consulta dentro do ttl e refaz depois que expira`() {
        val clock = MutableClock(Instant.parse("2026-08-21T12:00:00Z"))
        val cache = PixStatusCache(properties, clock)
        val consultas = AtomicInteger()

        repeat(5) { cache.obter("cnpj|ref") { statusEmAberto(consultas) } }
        assertEquals(1, consultas.get())

        clock.avancar(Duration.ofSeconds(46))
        cache.obter("cnpj|ref") { statusEmAberto(consultas) }
        assertEquals(2, consultas.get())
    }

    @Test
    fun `transacao paga usa o ttl estendido`() {
        val clock = MutableClock(Instant.parse("2026-08-21T12:00:00Z"))
        val cache = PixStatusCache(properties, clock)
        val consultas = AtomicInteger()

        cache.obter("cnpj|ref") { statusPago(consultas) }
        clock.avancar(Duration.ofMinutes(5))
        cache.obter("cnpj|ref") { statusPago(consultas) }

        assertEquals(1, consultas.get())
    }

    @Test
    fun `ignorarCache forca nova consulta`() {
        val clock = MutableClock(Instant.parse("2026-08-21T12:00:00Z"))
        val cache = PixStatusCache(properties, clock)
        val consultas = AtomicInteger()

        cache.obter("cnpj|ref") { statusEmAberto(consultas) }
        cache.obter("cnpj|ref", ignorarCache = true) { statusEmAberto(consultas) }

        assertEquals(2, consultas.get())
    }

    @Test
    fun `devolve o ultimo status conhecido quando a consulta falha`() {
        val clock = MutableClock(Instant.parse("2026-08-21T12:00:00Z"))
        val cache = PixStatusCache(properties, clock)
        val consultas = AtomicInteger()

        val primeira = cache.obter("cnpj|ref") { statusEmAberto(consultas) }
        assertFalse(primeira.desatualizado)

        clock.avancar(Duration.ofSeconds(46))
        val segunda = cache.obter("cnpj|ref") {
            throw PixProviderRateLimitException(Duration.ofSeconds(30))
        }

        assertTrue(segunda.desatualizada())
        assertEquals("Aguardando pagamento", segunda.status)
        assertEquals(Instant.parse("2026-08-21T12:00:00Z"), segunda.consultadoEm)
    }

    @Test
    fun `propaga o erro quando nao existe status anterior`() {
        val cache = PixStatusCache(properties, MutableClock(Instant.parse("2026-08-21T12:00:00Z")))

        assertFailsWith<PixProviderRateLimitException> {
            cache.obter("cnpj|ref") { throw PixProviderRateLimitException(Duration.ofSeconds(30)) }
        }
    }

    @Test
    fun `chamadas concorrentes da mesma transacao viram uma unica consulta`() {
        val cache = PixStatusCache(properties, MutableClock(Instant.parse("2026-08-21T12:00:00Z")))
        val consultas = AtomicInteger()
        val threads = 8
        val largada = CountDownLatch(1)
        val chegada = CountDownLatch(threads)
        val executor = Executors.newFixedThreadPool(threads)

        repeat(threads) {
            executor.submit {
                largada.await()
                cache.obter("cnpj|ref") {
                    Thread.sleep(50)
                    statusEmAberto(consultas)
                }
                chegada.countDown()
            }
        }

        largada.countDown()
        assertTrue(chegada.await(10, TimeUnit.SECONDS))
        executor.shutdownNow()

        assertEquals(1, consultas.get())
    }

    private fun UzziPixTransactionStatus.desatualizada(): Boolean = desatualizado

    private fun statusEmAberto(consultas: AtomicInteger) = UzziPixTransactionStatus(
        txId = "PIX-1",
        paid = false,
        status = "Aguardando pagamento",
    ).also { consultas.incrementAndGet() }

    private fun statusPago(consultas: AtomicInteger) = UzziPixTransactionStatus(
        txId = "PIX-1",
        paid = true,
        status = "Pago",
    ).also { consultas.incrementAndGet() }

    private class MutableClock(private var instante: Instant) : Clock() {
        fun avancar(duracao: Duration) {
            instante = instante.plus(duracao)
        }

        override fun instant(): Instant = instante
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }
}
