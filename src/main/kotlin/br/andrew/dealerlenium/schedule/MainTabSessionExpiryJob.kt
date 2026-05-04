package br.andrew.dealerlenium.schedule

import br.andrew.dealerlenium.browser.BrowserRuntime
import org.openqa.selenium.WebDriverException
import org.springframework.stereotype.Component

@Component
class MainTabSessionExpiryJob {

    @Volatile
    private var delayResetRequested = false

    fun run(): Boolean {
        return runCatching {
            BrowserRuntime.xpath("//div[contains(@class,'x-window-dlg') and contains(@style,'visibility: visible')]")
                .takeIf { it.exists() }
                ?.takeIf {
                    it.`$x`(".//span[contains(., 'Clique aqui para voltar para o sistema')]").exists()
                }
                ?.`$x`(".//button[normalize-space()='OK']")
                ?.takeIf { it.exists() }
                ?.let {
                    it.click()
                    true
                }
                ?: false
        }.getOrElse { error ->
            if (error is WebDriverException) {
                false
            } else {
                throw error
            }
        }
    }

    fun requestDelayReset() {
        delayResetRequested = true
    }

    fun consumeDelayResetRequest(): Boolean {
        val shouldReset = delayResetRequested
        delayResetRequested = false
        return shouldReset
    }
}
