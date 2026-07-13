package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.DealerProperties
import com.codeborne.selenide.SelenideConfig
import com.codeborne.selenide.SelenideDriver
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

/**
 * Cria drivers Chrome/Selenide isolados para o pool de sessoes do dealer.
 *
 * Cada sessao recebe seu proprio perfil de Chrome (`--user-data-dir`), garantindo
 * que dois navegadores nao compartilhem estado de perfil; combinado com logins
 * distintos, cada sessao tem seu proprio `ASP.NET_SessionId`.
 */
@Component
class DealerBrowserFactory(
    private val dealerProperties: DealerProperties,
) {
    /**
     * Cria um ChromeDriver dedicado, com um diretorio de perfil exclusivo.
     * Retorna o driver e o diretorio criado para que o chamador possa apaga-lo no shutdown.
     */
    fun createDriver(): DealerDriverHandle {
        configureChromeRuntime()
        val profileDir = Files.createTempDirectory("dealer-session-profile-")
        val options = createChromeOptions()
        options.addArguments("--user-data-dir=${profileDir.toAbsolutePath()}")
        val webDriver: WebDriver = ChromeDriver(options)
        return DealerDriverHandle(webDriver, profileDir)
    }

    fun createSelenideDriver(webDriver: WebDriver): SelenideDriver {
        val config = SelenideConfig()
            .timeout(10_000)
            .screenshots(dealerProperties.screenshotsEnabled)
            .savePageSource(dealerProperties.screenshotsEnabled)
        return SelenideDriver(config, webDriver, null)
    }

    private fun createChromeOptions(): ChromeOptions {
        val options = ChromeOptions()
        options.addArguments("--no-sandbox")
        options.addArguments("--disable-dev-shm-usage")
        options.addArguments("--disable-gpu")

        resolveChromeBinaryPath()?.let(options::setBinary)

        resolveChromeDriverPath()?.let {
            System.setProperty("webdriver.chrome.driver", it)
        }

        if (dealerProperties.headless) {
            options.addArguments("--headless=new")
        }
        if (dealerProperties.fullscreen && !dealerProperties.headless) {
            options.addArguments("--start-maximized")
        }

        return options
    }

    private fun configureChromeRuntime() {
        resolveChromeDriverPath()?.let {
            System.setProperty("webdriver.chrome.driver", it)
        }
    }

    private fun resolveChromeBinaryPath(): String? {
        return listOf(
            "/usr/bin/google-chrome",
            "/opt/google/chrome/chrome",
            "/usr/local/bin/chrome",
            "/usr/lib/chromium/chromium",
            "/usr/lib/chromium-browser/chromium-browser",
            System.getenv("CHROME_BIN"),
            "/usr/bin/chromium",
            "/usr/bin/chromium-browser",
        ).firstNotNullOfOrNull(::existingExecutablePathOrNull)
    }

    private fun resolveChromeDriverPath(): String? {
        return listOf(
            "/usr/lib/chromium/chromedriver",
            "/usr/local/bin/chromedriver",
            "/usr/bin/chromedriver",
            System.getenv("CHROMEDRIVER_PATH"),
        ).firstNotNullOfOrNull(::existingExecutablePathOrNull)
    }

    private fun existingExecutablePathOrNull(rawPath: String?): String? {
        val path = rawPath?.trim().orEmpty()
        if (path.isBlank()) {
            return null
        }

        return path.takeIf {
            val resolvedPath = Path.of(it)
            Files.exists(resolvedPath) && Files.isRegularFile(resolvedPath) && Files.isExecutable(resolvedPath)
        }
    }
}

data class DealerDriverHandle(
    val webDriver: WebDriver,
    val profileDir: Path,
)

internal fun isDealerLoginPageUrl(currentUrl: String?): Boolean {
    return DEALER_LOGIN_PAGE_PATTERN.containsMatchIn(currentUrl.orEmpty())
}

private val DEALER_LOGIN_PAGE_PATTERN = Regex(
    pattern = """login(?:aux)?\.aspx""",
    option = RegexOption.IGNORE_CASE,
)
