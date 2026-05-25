package br.andrew.dealerlenium

import br.andrew.dealerlenium.service.BrowserSessionManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(prefix = "dealer", name = ["startup-login-enabled"], havingValue = "true", matchIfMissing = true)
class LoginAutomationRunner(
    private val browserSessionManager: BrowserSessionManager,
) : CommandLineRunner {
    override fun run(vararg args: String) {
        browserSessionManager.initialize()
    }
}
