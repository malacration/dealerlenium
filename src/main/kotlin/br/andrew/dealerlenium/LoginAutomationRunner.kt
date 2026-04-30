package br.andrew.dealerlenium

import br.andrew.dealerlenium.service.BrowserSessionManager
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Service

@Service
class LoginAutomationRunner(
    private val browserSessionManager: BrowserSessionManager,
) : CommandLineRunner {
    override fun run(vararg args: String) {
        browserSessionManager.initialize()
    }
}
