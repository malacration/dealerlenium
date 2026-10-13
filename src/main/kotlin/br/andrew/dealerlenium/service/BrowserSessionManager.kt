package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.DealerProperties
import br.andrew.dealerlenium.infrastructure.configurations.Empresa
import br.andrew.dealerlenium.infrastructure.configurations.EmpresaProperties
import br.andrew.dealerlenium.pages.HomePage
import br.andrew.dealerlenium.pages.LoginPage
import br.andrew.dealerlenium.schedule.MainTabSessionExpiryJob
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Pool de sessoes independentes do dealer.
 *
 * Cada credencial configurada ([DealerProperties.resolveCredentials]) vira uma
 * [DealerSession] com login proprio e, portanto, com `ASP.NET_SessionId` proprio.
 * As requisicoes pegam emprestada uma sessao exclusiva ([runInSession]); como o
 * GeneXus mantem o estado do grid no servidor indexado pela sessao, isso evita que
 * buscas concorrentes contaminem o estado uma da outra.
 *
 * Cada credencial declara o alcance de filiais que atende
 * ([DealerCredential.empresa][br.andrew.dealerlenium.DealerCredential.empresa]), o que
 * cria duas formas de transacao:
 *
 * - **com lock de filial**: o chamador informa a filial; se existir um login dedicado
 *   a ela, a transacao usa aquele login, que ja esta posicionado na filial desde o
 *   login (a troca de filial vira no-op). Se a sessao dedicada estiver ocupada, a
 *   requisicao espera ela liberar - a filial dedicada nunca cai no pool geral;
 * - **geral**: o chamador nao sabe a filial (busca de titulo com filtro `all`), e a
 *   requisicao usa os logins gerais / `all`.
 *
 * Com apenas uma credencial o pool tem tamanho 1 e serializa as requisicoes
 * naturalmente (uma e resolvida depois da outra).
 */
@Service
class BrowserSessionManager(
    private val dealerProperties: DealerProperties,
    private val empresaProperties: EmpresaProperties,
    private val browserFactory: DealerBrowserFactory,
    private val loginPage: LoginPage,
    private val homePage: HomePage,
    private val mainTabSessionExpiryJob: MainTabSessionExpiryJob,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val sessions: List<DealerSession> by lazy { buildSessions() }
    private val routing: DealerSessionRouting by lazy { DealerSessionRouting(sessions.map { it.escopo }) }

    private val lock = ReentrantLock(true)
    private val sessaoLiberada = lock.newCondition()
    private val livres: MutableList<DealerSession> by lazy {
        sessions.toMutableList()
    }

    private val sessionExpiryMonitorExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "dealer-session-expiry-monitor").apply { isDaemon = true }
        }

    @Volatile
    private var sessionExpiryMonitorStarted = false

    private fun buildSessions(): List<DealerSession> {
        val credentials = dealerProperties.resolveCredentials()
        val sessoes = credentials.mapIndexed { index, credential ->
            val escopo = DealerSessionScope.of(credential.empresa)
            val empresaFixa: Empresa? = (escopo as? DealerSessionScope.Dedicada)
                ?.let { empresaProperties.getEmpresaOrThrow(it.empresaId) }
            DealerSession(
                id = index + 1,
                credential = credential,
                escopo = escopo,
                empresaFixa = empresaFixa,
                dealerProperties = dealerProperties,
                browserFactory = browserFactory,
                loginPage = loginPage,
                homePage = homePage,
                mainTabSessionExpiryJob = mainTabSessionExpiryJob,
            )
        }
        logPool(sessoes)
        return sessoes
    }

    private fun logPool(sessoes: List<DealerSession>) {
        logger.info("Inicializando pool de sessoes do dealer com {} credencial(is)", sessoes.size)
        sessoes.forEach { sessao ->
            val descricao = when (val escopo = sessao.escopo) {
                is DealerSessionScope.Dedicada -> "dedicada a filial ${escopo.empresaId}"
                DealerSessionScope.Todas -> "atende todas as filiais"
                DealerSessionScope.Geral -> "geral"
            }
            logger.info("Sessao dealer #{}: login='{}', escopo={}", sessao.id, sessao.username, descricao)
        }
        // Nao da para usar o campo [routing]: ele depende de [sessions], que ainda esta sendo criado.
        val roteamento = DealerSessionRouting(sessoes.map { it.escopo })
        if (roteamento.filiaisDedicadas.isNotEmpty()) {
            logger.info("Filiais fora do pool geral (tem login dedicado): {}", roteamento.filiaisDedicadas)
        }
        empresaProperties.listEmpresas()
            .map { it.id }
            .filterNot { roteamento.atendida(DealerSessionScope.normalizar(it)) }
            .forEach { logger.warn("Nenhuma credencial do dealer atende a filial {}", it) }
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
     * Bloqueia enquanto nao houver sessao compativel livre (backpressure).
     *
     * [empresa] e a filial da transacao; `null` ou `all` significam "sem filial" e
     * caem no pool geral. Quando a filial e informada, o pool garante que a sessao
     * esta posicionada nela antes de executar [action].
     */
    fun <T> runInSession(empresa: String? = null, action: (HomePage) -> T): T {
        val alvo = DealerSessionScope.alvoDe(empresa)
        val session = borrow(alvo)
        return try {
            runWithRetry(session, alvo, action, attempt = 0)
        } finally {
            release(session)
        }
    }

    private fun borrow(alvo: String?): DealerSession {
        if (!routing.atendida(alvo)) {
            throw IllegalStateException(
                "Nenhuma credencial do dealer atende a filial '$alvo'. Revise dealer.credentials[].empresa.",
            )
        }
        lock.withLock {
            while (true) {
                val session = routing.escolher(livres, { it.escopo }, alvo)
                if (session != null) {
                    livres.remove(session)
                    return session
                }
                sessaoLiberada.await()
            }
        }
    }

    private fun release(session: DealerSession) {
        lock.withLock {
            livres.add(session)
            sessaoLiberada.signalAll()
        }
    }

    /** Remove a sessao do pool sem bloquear; `false` quando ela esta em uso. */
    private fun tryBorrow(session: DealerSession): Boolean = lock.withLock { livres.remove(session) }

    private fun <T> runWithRetry(session: DealerSession, alvo: String?, action: (HomePage) -> T, attempt: Int): T {
        return try {
            session.runInTab { homePage ->
                logger.info(
                    "Executando operacao dealer: sessao={}, login='{}', escopo={}, filialAlvo={}, tentativa={}",
                    session.id, session.username, session.escopo, alvo, attempt + 1,
                )
                naEtapa("troca de filial") { garantirFilial(session, alvo, homePage) }
                action(homePage)
            }
        } catch (error: DealerSessionExpiredException) {
            if (attempt < MAX_RETRIES) {
                logger.warn("Sessao #{} expirada; refazendo login e tentando novamente (tentativa {})", session.id, attempt + 1)
                runCatching { session.refresh() }
                runWithRetry(session, alvo, action, attempt + 1)
            } else {
                throw error
            }
        } catch (error: StaleDealerReadException) {
            if (attempt < MAX_RETRIES) {
                logger.warn("Leitura suja na sessao #{}; repetindo a operacao (tentativa {}): {}", session.id, attempt + 1, error.message)
                runWithRetry(session, alvo, action, attempt + 1)
            } else {
                throw error
            }
        }
    }

    /**
     * Garante que a sessao esta na filial da transacao. Numa sessao dedicada a filial
     * ja e a correta desde o login, entao nada e feito.
     */
    private fun garantirFilial(session: DealerSession, alvo: String?, homePage: HomePage) {
        if (alvo == null) {
            return
        }
        val escopo = session.escopo
        if (escopo is DealerSessionScope.Dedicada && escopo.empresaId == alvo) {
            return
        }
        homePage.changeFilial(empresaProperties.getEmpresaOrThrow(alvo))
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
     * Roda o job de expiracao apenas em sessoes ociosas, sem bloquear requisicoes em
     * andamento. E o que mantem vivo o slot de licenca de cada login.
     */
    private fun runExpiryJobOnIdleSessions() {
        sessions.forEach { session ->
            if (!tryBorrow(session)) {
                return@forEach
            }
            try {
                session.runExpiryJobIfDue()
            } catch (_: RuntimeException) {
            } finally {
                release(session)
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

/**
 * Falha da automacao do dealer marcada com a [etapa] em que aconteceu. A mensagem vai
 * para o front (erro da API e `ultimaFalhaProcessamento` da transacao), entao carrega
 * a etapa e so a primeira linha do erro original.
 */
class DealerEtapaException(val etapa: String, cause: Throwable) : RuntimeException(
    "Falha durante a $etapa no dealer: ${cause.message?.lineSequence()?.firstOrNull()?.trim().orEmpty().ifBlank { cause::class.simpleName }}",
    cause,
)

/**
 * Executa [action] marcando as falhas com a [etapa]. As excecoes que controlam o retry
 * do pool passam direto.
 */
internal fun <T> naEtapa(etapa: String, action: () -> T): T {
    return try {
        action()
    } catch (error: DealerSessionExpiredException) {
        throw error
    } catch (error: StaleDealerReadException) {
        throw error
    } catch (error: DealerEtapaException) {
        throw error
    } catch (error: IllegalArgumentException) {
        // Entrada invalida (ex.: filial desconhecida) continua virando 400 na API.
        throw error
    } catch (error: Exception) {
        throw DealerEtapaException(etapa, error)
    }
}

/** A navegacao do dealer foi redirecionada para a pagina de login. */
class DealerSessionExpiredException : RuntimeException("Dealer session redirected to login page.")

/**
 * O grid do dealer devolveu um registro diferente do solicitado (leitura suja /
 * estado de sessao defasado). Dispara nova tentativa na mesma sessao.
 */
class StaleDealerReadException(message: String) : RuntimeException(message)
