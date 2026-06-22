package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.DealerCredential
import br.andrew.dealerlenium.DealerProperties
import br.andrew.dealerlenium.browser.BrowserRuntime
import br.andrew.dealerlenium.pages.HomePage
import br.andrew.dealerlenium.pages.LoginPage
import br.andrew.dealerlenium.schedule.MainTabSessionExpiryJob
import com.codeborne.selenide.SelenideDriver
import org.openqa.selenium.NoSuchSessionException
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebDriverException
import org.openqa.selenium.WindowType
import org.openqa.selenium.remote.RemoteWebDriver
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Comparator
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Uma sessao independente do dealer: um navegador Chrome dedicado, logado com uma
 * credencial propria, portanto com seu proprio `ASP.NET_SessionId`.
 *
 * Toda interacao com o WebDriver acontece na thread fixa desta sessao
 * ([executor]), pois o WebDriver nao e thread-safe. O pool garante que apenas uma
 * requisicao usa a sessao por vez; mesmo assim, fixar a thread evita reentrancia.
 */
class DealerSession(
    val id: Int,
    private val credential: DealerCredential,
    private val dealerProperties: DealerProperties,
    private val browserFactory: DealerBrowserFactory,
    private val loginPage: LoginPage,
    private val homePage: HomePage,
    private val mainTabSessionExpiryJob: MainTabSessionExpiryJob,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dealer-session-$id-worker").apply { isDaemon = false }
    }

    @Volatile
    private var initialized = false
    private var nextSessionExpiryJobRunAt: Instant = Instant.MIN

    private var selenideDriver: SelenideDriver? = null
    private var webDriver: WebDriver? = null
    private var profileDir: Path? = null
    private var mainWindowHandle: String = ""

    /** Executa um bloco na thread fixa desta sessao, com o SelenideDriver no contexto. */
    private fun <T> onSessionThread(action: () -> T): T {
        val future = executor.submit(Callable<T> {
            val driver = selenideDriver
                ?: return@Callable action()
            BrowserRuntime.withDriver(driver) { action() }
        })
        return try {
            future.get()
        } catch (error: java.util.concurrent.ExecutionException) {
            throw error.cause ?: error
        }
    }

    fun initialize() {
        onSessionThread { initializeIfNeeded() }
    }

    fun isAlive(): Boolean = onSessionThread { isSessionAlive() }

    /** Descarta a sessao morta e refaz o login desta credencial. */
    fun refresh() {
        onSessionThread {
            discardDriver()
            initializeIfNeeded()
        }
    }

    /**
     * Abre uma aba nova no [indexUrl][DealerProperties.indexUrl], executa [action] e
     * fecha a aba, voltando para a aba principal. Lanca [DealerSessionExpiredException]
     * se a navegacao cair na pagina de login.
     */
    fun <T> runInTab(action: (HomePage) -> T): T {
        return onSessionThread {
            initializeIfNeeded()
            runInSessionTab(action)
        }
    }

    fun runExpiryJobIfDue() {
        onSessionThread {
            if (initialized && isSessionAlive()) {
                runSessionExpiryJobOnMainTabIfDue()
            }
        }
    }

    fun shutdown() {
        try {
            onSessionThread {
                if (initialized) {
                    discardDriver()
                }
            }
        } catch (_: RuntimeException) {
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(1, TimeUnit.MINUTES)
        }
    }

    // --- internos (sempre na thread da sessao) ---

    private fun initializeIfNeeded() {
        if (initialized && isSessionAlive()) {
            runSessionExpiryJobOnMainTabIfDue()
            return
        }

        discardDriver()
        val handle = browserFactory.createDriver()
        webDriver = handle.webDriver
        profileDir = handle.profileDir
        val driver = browserFactory.createSelenideDriver(handle.webDriver)
        selenideDriver = driver

        BrowserRuntime.withDriver(driver) {
            loginPage.login(credential)
        }
        mainWindowHandle = handle.webDriver.windowHandle
        initialized = true
        nextSessionExpiryJobRunAt = Instant.now().plus(SESSION_EXPIRY_INITIAL_DELAY)
        runSessionExpiryJobOnMainTabIfDue()
        logger.info("Sessao dealer #{} inicializada para credencial '{}'", id, credential.username)
    }

    private fun <T> runInSessionTab(action: (HomePage) -> T): T {
        val driver = webDriver ?: throw DealerSessionExpiredException()
        val requestWindowHandle = driver.switchTo().newWindow(WindowType.TAB).windowHandle
        driver.switchTo().window(requestWindowHandle)
        driver.switchTo().defaultContent()

        return try {
            BrowserRuntime.open(dealerProperties.indexUrl)
            if (isDealerLoginPageUrl(driver.currentUrl)) {
                initialized = false
                throw DealerSessionExpiredException()
            }
            action(homePage)
        } finally {
            try {
                driver.switchTo().defaultContent()
                driver.close()
            } finally {
                runCatching {
                    driver.switchTo().window(mainWindowHandle)
                    driver.switchTo().defaultContent()
                }
            }
        }
    }

    private fun runSessionExpiryJobOnMainTabIfDue() {
        if (!dealerProperties.sessionExpiryJobEnabled) {
            return
        }
        val driver = webDriver ?: return
        val now = Instant.now()
        if (now.isBefore(nextSessionExpiryJobRunAt)) {
            return
        }

        driver.switchTo().window(mainWindowHandle)
        driver.switchTo().defaultContent()
        val handledSessionExpiry = mainTabSessionExpiryJob.run()
        val requestedDelayReset = mainTabSessionExpiryJob.consumeDelayResetRequest()

        nextSessionExpiryJobRunAt = if (handledSessionExpiry || requestedDelayReset) {
            now.plus(SESSION_EXPIRY_INITIAL_DELAY)
        } else {
            now.plus(SESSION_EXPIRY_RUN_INTERVAL)
        }
    }

    private fun isSessionAlive(): Boolean {
        if (!initialized) {
            return false
        }
        val driver = webDriver ?: return false
        val remoteDriver = driver as? RemoteWebDriver ?: return false
        val sessionId = remoteDriver.sessionId ?: return false

        return try {
            sessionId.toString().isNotBlank()
                && driver.windowHandles.contains(mainWindowHandle)
                && !isMainWindowOnLoginPage(driver)
        } catch (_: NoSuchSessionException) {
            false
        } catch (_: WebDriverException) {
            false
        }
    }

    private fun isMainWindowOnLoginPage(driver: WebDriver): Boolean {
        val currentWindowHandle = runCatching { driver.windowHandle }.getOrNull()
        return try {
            driver.switchTo().window(mainWindowHandle)
            isDealerLoginPageUrl(driver.currentUrl)
        } finally {
            if (currentWindowHandle != null && currentWindowHandle != mainWindowHandle) {
                runCatching { driver.switchTo().window(currentWindowHandle) }
            }
        }
    }

    private fun discardDriver() {
        val driver = webDriver
        if (driver != null) {
            runCatching { driver.quit() }
        }
        profileDir?.let(::deleteDirectoryQuietly)
        webDriver = null
        selenideDriver = null
        profileDir = null
        mainWindowHandle = ""
        initialized = false
    }

    private fun deleteDirectoryQuietly(directory: Path) {
        if (!Files.exists(directory)) {
            return
        }
        runCatching {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { path ->
                    runCatching { Files.deleteIfExists(path) }
                }
            }
        }
    }

    companion object {
        private val SESSION_EXPIRY_INITIAL_DELAY: Duration = Duration.ofSeconds(1)
        private val SESSION_EXPIRY_RUN_INTERVAL: Duration = Duration.ofSeconds(10)
    }
}
