package br.andrew.dealerlenium.pages

import br.andrew.dealerlenium.browser.BrowserRuntime
import org.openqa.selenium.By
import org.openqa.selenium.NoSuchFrameException
import org.openqa.selenium.StaleElementReferenceException
import org.openqa.selenium.TimeoutException
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class FrameSwitcher {
    fun frameBySrcExists(srcContains: String): Boolean {
        val cssValue = srcContains.replace("\\", "\\\\").replace("'", "\\'")
        val frameSelector = "iframe[src*='$cssValue' i], frame[src*='$cssValue' i]"
        return BrowserRuntime.getWebDriver()
            .findElements(By.cssSelector(frameSelector))
            .isNotEmpty()
    }

    fun switchToFrameBySrc(
        srcContains: String,
        timeoutSeconds: Long = 15,
        fromRoot: Boolean = false,
    ) {
        if (fromRoot) {
            BrowserRuntime.switchTo().defaultContent()
        }
        val cssValue = srcContains.replace("\\", "\\\\").replace("'", "\\'")
        val frameSelector = "iframe[src*='$cssValue' i], frame[src*='$cssValue' i]"
        val wait = WebDriverWait(BrowserRuntime.getWebDriver(), Duration.ofSeconds(timeoutSeconds))
        try {
            wait.until(
                ExpectedConditions.frameToBeAvailableAndSwitchToIt(By.cssSelector(frameSelector)),
            )
        } catch (_: TimeoutException) {
            error("Nao foi encontrado iframe/frame com src contendo: $srcContains")
        }
    }

    fun switchToParentFrameWhenReady(timeoutSeconds: Long = 10) {
        val wait = WebDriverWait(BrowserRuntime.getWebDriver(), Duration.ofSeconds(timeoutSeconds))
            .ignoring(StaleElementReferenceException::class.java)
            .ignoring(NoSuchFrameException::class.java)
        try {
            wait.until {
                BrowserRuntime.switchTo().parentFrame()
                true
            }
        } catch (_: TimeoutException) {
            error("Nao foi possivel trocar para o iframe superior dentro de ${timeoutSeconds}s")
        }
    }

    fun afterCloseCurrentFrameSwitchToParent(action: () -> Unit, timeoutSeconds: Long = 10) {
        action()
        switchToParentFrameWhenReady(timeoutSeconds)
    }
}
