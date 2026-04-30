package br.andrew.dealerlenium

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "dealer")
data class DealerProperties(
    val loginUrl: String,
    val indexUrl: String,
    @field:NotBlank val username: String,
    @field:NotBlank val password: String,
    val headless: Boolean = true,
    val sessionExpiryJobEnabled: Boolean = true,
    val autoCloseBrowser: Boolean = true,
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
    val adiantamento: AdiantamentoProperties = AdiantamentoProperties(),
) {

}

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
    val pessoasReceberSelector: String = "xpath=//li[.//span[contains(normalize-space(.),'Pessoas')]]",
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

data class AdiantamentoProperties(
    val contaGerencialIdentificador: String = "30203",
    val tipoCodigo: String = "5",
    val tipoDocumentoCodigo: String = "176",
    val departamento: String = "ADMINISTRAÇÃO",
    val tipoFichaRazao: String = "ADIANT DE CLIENTES (PEÇAS)",
)
