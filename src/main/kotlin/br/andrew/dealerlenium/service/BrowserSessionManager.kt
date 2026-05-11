package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.DealerProperties
import br.andrew.dealerlenium.browser.BrowserRuntime
import br.andrew.dealerlenium.pages.HomePage
import br.andrew.dealerlenium.pages.LoginPage
import br.andrew.dealerlenium.schedule.MainTabSessionExpiryJob
import com.codeborne.selenide.Configuration
import com.codeborne.selenide.Selenide.open
import com.codeborne.selenide.SelenideConfig
import com.codeborne.selenide.SelenideDriver
import com.codeborne.selenide.WebDriverRunner
import jakarta.annotation.PreDestroy
import org.openqa.selenium.NoSuchSessionException
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebDriverException
import org.openqa.selenium.WindowType
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.RemoteWebDriver
import org.springframework.stereotype.Service
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant
import java.util.Comparator
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.io.path.deleteIfExists

@Service
class BrowserSessionManager(
    private val dealerProperties: DealerProperties,
    private val loginPage: LoginPage,
    private val homePage: HomePage,
    private val mainTabSessionExpiryJob: MainTabSessionExpiryJob,
) {
    private val browserExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dealer-browser-worker").apply {
            isDaemon = false
        }
    }
    private val sessionExpiryMonitorExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "dealer-session-expiry-monitor").apply {
                isDaemon = true
            }
        }

    @Volatile
    private var initialized = false
    @Volatile
    private var sessionExpiryMonitorStarted = false
    private var nextSessionExpiryJobRunAt: Instant = Instant.MIN

    private lateinit var sharedWebDriver: WebDriver
    private lateinit var mainWindowHandle: String

    fun initialize() {
        startSessionExpiryMonitorIfNeeded()
        runOnBrowserThread {
            initializeSessionIfNeeded()
        }
    }

    fun <T> runInNewTab(action: (HomePage) -> T): T {
        return runOnBrowserThread {
            initializeSessionIfNeeded()
            runInSessionTab(dealerProperties.indexUrl, action)
        }
    }

    @PreDestroy
    fun shutdown() {
        try {
            runOnBrowserThread {
                if (initialized && isSessionAlive()) {
                    WebDriverRunner.closeWebDriver()
                }
                initialized = false
            }
        } catch (_: RuntimeException) {
        } finally {
            sessionExpiryMonitorExecutor.shutdownNow()
            sessionExpiryMonitorExecutor.awaitTermination(5, TimeUnit.MINUTES)
            browserExecutor.shutdownNow()
            browserExecutor.awaitTermination(5, TimeUnit.MINUTES)
        }
    }

    private fun initializeSessionIfNeeded() {
        if (initialized && isSessionAlive()) {
            runSessionExpiryJobOnMainTabIfDue()
            return
        }

        discardDeadSessionReference()
        Configuration.headless = dealerProperties.headless
        Configuration.browserCapabilities = createChromeOptions()
        loginPage.login()

        sharedWebDriver = WebDriverRunner.getWebDriver()
        mainWindowHandle = sharedWebDriver.windowHandle
        initialized = true
        resetSessionExpiryJobSchedule(Instant.now())
        runSessionExpiryJobOnMainTabIfDue()
    }

    private fun runSessionExpiryJobOnMainTabIfDue() {
        if (!dealerProperties.sessionExpiryJobEnabled) {
            return
        }

        val now = Instant.now()
        if (now.isBefore(nextSessionExpiryJobRunAt)) {
            return
        }

        sharedWebDriver.switchTo().window(mainWindowHandle)
        sharedWebDriver.switchTo().defaultContent()
        val handledSessionExpiry = mainTabSessionExpiryJob.run()
        val requestedDelayReset = mainTabSessionExpiryJob.consumeDelayResetRequest()

        nextSessionExpiryJobRunAt = if (handledSessionExpiry || requestedDelayReset) {
            now.plus(SESSION_EXPIRY_INITIAL_DELAY)
        } else {
            now.plus(SESSION_EXPIRY_RUN_INTERVAL)
        }
    }

    private fun resetSessionExpiryJobSchedule(referenceTime: Instant) {
        nextSessionExpiryJobRunAt = referenceTime.plus(SESSION_EXPIRY_INITIAL_DELAY)
    }

    private fun startSessionExpiryMonitorIfNeeded() {
        if (!dealerProperties.sessionExpiryJobEnabled) {
            return
        }

        if (sessionExpiryMonitorStarted) {
            return
        }

        synchronized(this) {
            if (sessionExpiryMonitorStarted) {
                return
            }

            sessionExpiryMonitorExecutor.scheduleWithFixedDelay(
                {
                    try {
                        runOnBrowserThread {
                            if (initialized && isSessionAlive()) {
                                runSessionExpiryJobOnMainTabIfDue()
                            }
                        }
                    } catch (_: RuntimeException) {
                    }
                },
                SESSION_EXPIRY_RUN_INTERVAL.toMillis(),
                SESSION_EXPIRY_RUN_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS,
            )

            sessionExpiryMonitorStarted = true
        }
    }

    private fun isSessionAlive(): Boolean {
        if (!initialized) {
            return false
        }

        val remoteDriver = sharedWebDriver as? RemoteWebDriver ?: return false
        val sessionId = remoteDriver.sessionId ?: return false

        return try {
            sessionId.toString().isNotBlank()
                && sharedWebDriver.windowHandles.contains(mainWindowHandle)
        } catch (_: NoSuchSessionException) {
            false
        } catch (_: WebDriverException) {
            false
        }
    }

    private fun discardDeadSessionReference() {
        if (::sharedWebDriver.isInitialized) {
            try {
                WebDriverRunner.closeWebDriver()
            } catch (_: RuntimeException) {
            }
        }
        initialized = false
    }

    private fun <T> runOnBrowserThread(action: () -> T): T {
        val future = browserExecutor.submit(Callable<T> { action() })
        return future.get()
    }

    fun <T> runInClonedStateDriver(path : String  = "", action: (HomePage) -> T): T {
        val snapshot = runOnBrowserThread {
            initializeSessionIfNeeded()
            captureSessionState(path)
        }

        val copiedProfileDir = copyProfileDirectory(snapshot.userDataDir)
        val clonedWebDriver = createExperimentalDriver(copiedProfileDir)
        val clonedSelenideDriver = createSelenideDriver(clonedWebDriver)

        return try {
            BrowserRuntime.withDriver(clonedSelenideDriver) {
                clonedSelenideDriver.open(snapshot.currentUrl)
                action(homePage)
            }
        } finally {
            try {
                clonedSelenideDriver.close()
            } catch (_: RuntimeException) {
            }
            deleteDirectoryQuietly(copiedProfileDir)
        }
    }

    private fun <T> runInSessionTab(targetUrl: String, action: (HomePage) -> T): T {
        val requestWindowHandle = sharedWebDriver.switchTo().newWindow(WindowType.TAB).windowHandle
        sharedWebDriver.switchTo().window(requestWindowHandle)
        sharedWebDriver.switchTo().defaultContent()
        open(targetUrl)

        return try {
            action(homePage)
        } finally {
            try {
                sharedWebDriver.switchTo().defaultContent()
                sharedWebDriver.close()
            } finally {
                sharedWebDriver.switchTo().window(mainWindowHandle)
                sharedWebDriver.switchTo().defaultContent()
            }
        }
    }

    private fun captureSessionState(path : String = ""): BrowserSessionSnapshot {
        val currentUrl = sharedWebDriver.currentUrl ?: dealerProperties.indexUrl

        return BrowserSessionSnapshot(
            currentUrl = resolveSnapshotUrl(currentUrl, path),
            userDataDir = resolveUserDataDir(sharedWebDriver),
        )
    }

    private fun resolveSnapshotUrl(currentUrl: String, path: String): String {
        val baseUrl = currentUrl.ifBlank { dealerProperties.indexUrl }
        val rawPath = path.trim()

        if (rawPath.isBlank()) {
            return baseUrl
        }

        val requestedUri = runCatching { URI(rawPath) }.getOrNull()
        if (requestedUri?.isAbsolute == true) {
            return rawPath
        }

        val baseUri = runCatching { URI(baseUrl) }.getOrNull() ?: return rawPath
        val normalizedPath = rawPath.takeIf { it.startsWith("/") } ?: "/$rawPath"

        return URI(baseUri.scheme, baseUri.authority, normalizedPath, null, null).toString()
    }

    private fun createSelenideDriver(webDriver: WebDriver): SelenideDriver {
        return SelenideDriver(SelenideConfig(), webDriver, null)
    }

    private fun createExperimentalDriver(profileDir: Path): WebDriver {
        val options = createChromeOptions()
        options.addArguments("--user-data-dir=${profileDir.toAbsolutePath()}")
        return ChromeDriver(options)
    }

    private fun createChromeOptions(): ChromeOptions {
        val options = ChromeOptions()
        options.addArguments("--no-sandbox")
        options.addArguments("--disable-dev-shm-usage")
        options.addArguments("--disable-gpu")

        val chromeBinary = System.getenv("CHROME_BIN")?.trim().orEmpty()
        if (chromeBinary.isNotBlank()) {
            options.setBinary(chromeBinary)
        }

        if (dealerProperties.headless) {
            options.addArguments("--headless=new")
        }
        if (dealerProperties.fullscreen && !dealerProperties.headless) {
            options.addArguments("--start-maximized")
        }

        return options
    }

    private fun resolveUserDataDir(driver: WebDriver): Path {
        val remoteDriver = driver as? RemoteWebDriver
            ?: error("Nao foi possivel obter o profile do WebDriver atual.")

        val chromeCapability = remoteDriver.capabilities.getCapability("chrome") as? Map<*, *>
        val userDataDir = chromeCapability?.get("userDataDir")?.toString()
            ?: error("Nao foi possivel localizar chrome.userDataDir nas capabilities do driver.")

        return Path.of(userDataDir)
    }

    private fun copyProfileDirectory(sourceDir: Path): Path {
        val targetDir = Files.createTempDirectory("dealer-cloned-profile-")

        Files.walk(sourceDir).use { paths ->
            paths.forEach { sourcePath ->
                val relativePath = sourceDir.relativize(sourcePath)
                val targetPath = targetDir.resolve(relativePath.toString())

                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath)
                    return@forEach
                }

                if (!Files.isRegularFile(sourcePath)) {
                    return@forEach
                }

                if (isChromeLockArtifact(relativePath)) {
                    return@forEach
                }

                try {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: Exception) {
                }
            }
        }

        removeChromeLockArtifacts(targetDir)
        return targetDir
    }

    private fun removeChromeLockArtifacts(profileDir: Path) {
        chromeLockArtifactNames().forEach { fileName ->
            profileDir.resolve(fileName).deleteIfExists()
        }
    }

    private fun deleteDirectoryQuietly(directory: Path) {
        if (!Files.exists(directory)) {
            return
        }

        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { path ->
                try {
                    Files.deleteIfExists(path)
                } catch (_: Exception) {
                }
            }
        }
    }

    private data class BrowserSessionSnapshot(
        val currentUrl: String,
        val userDataDir: Path,
    )

    private fun chromeLockArtifactNames(): List<String> {
        return listOf(
            "SingletonLock",
            "SingletonSocket",
            "SingletonCookie",
            "DevToolsActivePort",
        )
    }

    private fun isChromeLockArtifact(relativePath: Path): Boolean {
        return relativePath.nameCount == 1 && relativePath.fileName.toString() in chromeLockArtifactNames()
    }

    companion object {
        private val SESSION_EXPIRY_INITIAL_DELAY: Duration = Duration.ofSeconds(1)
        private val SESSION_EXPIRY_RUN_INTERVAL: Duration = Duration.ofSeconds(10)
    }
}
