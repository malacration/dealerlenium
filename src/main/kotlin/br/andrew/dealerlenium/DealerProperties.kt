package br.andrew.dealerlenium

import jakarta.validation.constraints.NotBlank
import br.andrew.dealerlenium.model.TipoTransacao
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "dealer")
data class DealerProperties(
    val loginUrl: String,
    val indexUrl: String,
    val username: String = "",
    val password: String = "",
    val credentials: List<DealerCredential> = emptyList(),
    val headless: Boolean = true,
    val startupLoginEnabled: Boolean = true,
    val sessionExpiryJobEnabled: Boolean = true,
    val autoCloseBrowser: Boolean = true,
    val screenshotsEnabled: Boolean = false,
    val fullscreen: Boolean = false,
    val flow: List<FlowStep> = listOf(
        FlowStep.LOGIN,
        FlowStep.CONTAS_RECEBER,
        FlowStep.HOME,
        FlowStep.TELA2,
        FlowStep.CREATE_DOCUMENT,
    ),
    val tipoTitulo: String,
    val navigation: NavigationProperties = NavigationProperties(),
    val captcha: CaptchaProperties = CaptchaProperties(),
    val transactionMonitoring: TransactionMonitoringProperties = TransactionMonitoringProperties(),
) {

    /**
     * Credenciais que compoem o pool de sessoes independentes do dealer.
     * Cada credencial vira uma sessao com login proprio (ASP.NET_SessionId proprio),
     * de modo que o tamanho do pool = numero de credenciais disponiveis.
     * Quando apenas [username]/[password] estao configurados, o pool tem tamanho 1
     * (serializa as requisicoes naturalmente).
     */
    fun resolveCredentials(): List<DealerCredential> {
        val configured = credentials
            .filter { it.username.isNotBlank() && it.password.isNotBlank() }
        if (configured.isNotEmpty()) {
            return configured
        }
        if (username.isNotBlank() && password.isNotBlank()) {
            return listOf(DealerCredential(username, password))
        }
        throw IllegalStateException(
            "Nenhuma credencial do dealer configurada. Defina dealer.credentials ou dealer.username/password.",
        )
    }
}

data class DealerCredential(
    @field:NotBlank val username: String = "",
    @field:NotBlank val password: String = "",
)

enum class FlowStep {
    LOGIN,
    CONTAS_RECEBER,
    XPTO,
    HOME,
    TELA2,
    CREATE_DOCUMENT,
}

data class NavigationProperties(
    val homeSelector: String = "#menuHome",
    val financeiroSelector: String = "xpath=//table[contains(concat('',normalize-space(@class),''),'x-toolbar-ct')]//button[contains(normalize-space(.), 'Financeiro')]",
    val cadastroSelector: String = "xpath=//table[contains(concat('',normalize-space(@class),''),'x-toolbar-ct')]//button[contains(normalize-space(.), 'Cadastro')]",
    val contasPagarHoverSelector: String = "xpath=//li[.//span[contains(normalize-space(.),'Contas a Pagar')]]",
    val pessoasSelector: String = "xpath=//li[.//span[contains(normalize-space(.),'Pessoas')]]",
    val titulosPagarSelector: String = "xpath=//li[.//span[contains(normalize-space(.),'Título a Pagar')]]",
    val contasReceberHoverSelector: String = "xpath=//li[.//span[contains(normalize-space(.),'Contas a Receber')]]",
    val tesourariaHoverSelector: String = "xpath=//li[.//span[contains(normalize-space(.),'Tesouraria')]]",
    val lancamentosSelector: String = "xpath=//li[.//span[contains(normalize-space(.),'Lançamentos')]]",
    val titulosReceberSelector: String = "xpath=//li[.//span[contains(normalize-space(.),'Título a Receber')]]",
    val tela2Selector: String = "#menuTela2",
    val createDocumentSelector: String = "#btnCriarDocumento",
)

data class CaptchaProperties(
    val imageSelector: String = "#captchaImage",
    val tessdataPath: String? = null,
    val language: String = "eng",
    val pageSegMode: Int = 8,
    val charWhitelist: String? = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz",
)

data class TransactionMonitoringProperties(
    val enabled: Boolean = true,
    val pollInterval: Duration = Duration.ofMinutes(1),
    val defaultCheckInterval: Duration = Duration.ofHours(1),
    val intervals: Map<TipoTransacao, Duration> = mapOf(
        TipoTransacao.ADIANTAMENTO to Duration.ofMinutes(5),
        TipoTransacao.CONTAS_RECEBER to Duration.ofHours(1),
    ),
)
