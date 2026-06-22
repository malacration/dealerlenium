package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.DealerProperties
import br.andrew.dealerlenium.pages.HomePage
import br.andrew.dealerlenium.pages.LoginPage
import br.andrew.dealerlenium.schedule.MainTabSessionExpiryJob
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Pool de sessoes independentes do dealer.
 *
 * Cada credencial configurada ([DealerProperties.resolveCredentials]) vira uma
 * [DealerSession] com login proprio e, portanto, com `ASP.NET_SessionId` proprio.
 * As requisicoes pegam emprestada uma sessao exclusiva ([runInSession]); como o
 * GeneXus mantem o estado do grid no servidor indexado pela sessao, isso evita que
 * buscas concorrentes contaminem o estado uma da outra.
 *
 * Com apenas uma credencial o pool tem tamanho 1, e o [available] serializa as
 * requisicoes naturalmente (uma e resolvida depois da outra).
 */
@Service
class BrowserSessionManager(
    private val dealerProperties: DealerProperties,
    private val browserFactory: DealerBrowserFactory,
    private val loginPage: LoginPage,
    private val homePage: HomePage,
    private val mainTabSessionExpiryJob: MainTabSessionExpiryJob,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val sessions: List<DealerSession> by lazy { buildSessions() }
    private val available: LinkedBlockingDeque<DealerSession> by lazy {
        LinkedBlockingDeque<DealerSession>().apply { sessions.forEach(::add) }
    }

    private val sessionExpiryMonitorExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "dealer-session-expiry-monitor").apply { isDaemon = true }
        }

    @Volatile
    private var sessionExpiryMonitorStarted = false

    private fun buildSessions(): List<DealerSession> {
        val credentials = dealerProperties.resolveCredentials()
        logger.info("Inicializando pool de sessoes do dealer com {} credencial(is)", credentials.size)
        return credentials.mapIndexed { index, credential ->
            DealerSession(
                id = index + 1,
                credential = credential,
                dealerProperties = dealerProperties,
                browserFactory = browserFactory,
                loginPage = loginPage,
                homePage = homePage,
                mainTabSessionExpiryJob = mainTabSessionExpiryJob,
            )
        }
    }

    fun initialize() {
        startSessionExpiryMonitorIfNeeded()
        sessions.forEach { session ->
            runCatching { session.initialize() }
                .onFailure { error ->
                    logger.error("Falha ao inicializar a sessao dealer #{}", session.id, error)
                }
        }
    }

    /**
     * Pega emprestada uma sessao exclusiva, executa [action] e devolve a sessao ao pool.
     * Bloqueia enquanto nao houver sessao livre (backpressure).
     */
    fun <T> runInSession(action: (HomePage) -> T): T {
        val session = available.takeFirst()
        return try {
            runWithRetry(session, action, attempt = 0)
        } finally {
            available.addLast(session)
        }
    }

    private fun <T> runWithRetry(session: DealerSession, action: (HomePage) -> T, attempt: Int): T {
        return try {
            session.runInTab(action)
        } catch (error: DealerSessionExpiredException) {
            if (attempt < MAX_RETRIES) {
                logger.warn("Sessao #{} expirada; refazendo login e tentando novamente (tentativa {})", session.id, attempt + 1)
                runCatching { session.refresh() }
                runWithRetry(session, action, attempt + 1)
            } else {
                throw error
            }
        } catch (error: StaleDealerReadException) {
            if (attempt < MAX_RETRIES) {
                logger.warn("Leitura suja na sessao #{}; repetindo a operacao (tentativa {}): {}", session.id, attempt + 1, error.message)
                runWithRetry(session, action, attempt + 1)
            } else {
                throw error
            }
        }
    }

    private fun startSessionExpiryMonitorIfNeeded() {
        if (!dealerProperties.sessionExpiryJobEnabled || sessionExpiryMonitorStarted) {
            return
        }
        synchronized(this) {
            if (sessionExpiryMonitorStarted) {
                return
            }
            sessionExpiryMonitorExecutor.scheduleWithFixedDelay(
                ::runExpiryJobOnIdleSessions,
                SESSION_EXPIRY_RUN_INTERVAL.toMillis(),
                SESSION_EXPIRY_RUN_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS,
            )
            sessionExpiryMonitorStarted = true
        }
    }

    /**
     * Roda o job de expiracao apenas em sessoes ociosas. Pega cada sessao livre do
     * pool, executa o job e a devolve, sem bloquear requisicoes em andamento.
     */
    private fun runExpiryJobOnIdleSessions() {
        val visited = mutableSetOf<Int>()
        while (true) {
            val session = available.pollFirst() ?: break
            if (!visited.add(session.id)) {
                // Demos a volta completa no pool: devolve e para.
                available.addLast(session)
                break
            }
            try {
                session.runExpiryJobIfDue()
            } catch (_: RuntimeException) {
            } finally {
                available.addLast(session)
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        try {
            sessions.forEach { session ->
                runCatching { session.shutdown() }
            }
        } finally {
            sessionExpiryMonitorExecutor.shutdownNow()
            sessionExpiryMonitorExecutor.awaitTermination(1, TimeUnit.MINUTES)
        }
    }

    companion object {
        private const val MAX_RETRIES = 2
        private val SESSION_EXPIRY_RUN_INTERVAL: Duration = Duration.ofSeconds(10)
    }
}

/** A navegacao do dealer foi redirecionada para a pagina de login. */
class DealerSessionExpiredException : RuntimeException("Dealer session redirected to login page.")

/**
 * O grid do dealer devolveu um registro diferente do solicitado (leitura suja /
 * estado de sessao defasado). Dispara nova tentativa na mesma sessao.
 */
class StaleDealerReadException(message: String) : RuntimeException(message)
