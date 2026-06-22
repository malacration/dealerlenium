package br.andrew.dealerlenium.pages

import br.andrew.dealerlenium.DealerCredential
import br.andrew.dealerlenium.DealerProperties
import br.andrew.dealerlenium.browser.BrowserRuntime
import br.andrew.dealerlenium.service.CaptchaOcrService
import com.codeborne.selenide.Condition.enabled
import com.codeborne.selenide.Condition.hidden
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.SelenideElement
import org.openqa.selenium.OutputType
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.time.Duration
import javax.imageio.ImageIO

@Component
class LoginPage(
    private val dealerProperties: DealerProperties,
    private val captchaOcrService: CaptchaOcrService,
    private val homePage: HomePage,
) {
    fun login(credential: DealerCredential): HomePage {
        BrowserRuntime.open(dealerProperties.loginUrl)
        if (dealerProperties.fullscreen && !dealerProperties.headless) {
            BrowserRuntime.getWebDriver().manage().window().maximize()
        }
        // Preenche usuario e senha de forma estavel: o campo de usuario do GeneXus
        // dispara AJAX no onchange/onblur que re-renderiza o formulario. Sem esperar
        // esse AJAX assentar, a senha pode cair/contaminar o campo de usuario (visto em
        // producao: gxoldvalue do usuario continha a senha). Por isso esperamos o AJAX e
        // lemos de volta cada campo, repetindo a sequencia ate ficar consistente.
        fillCredentialsStably(credential)

        //TODO se precisar resolver capcha concluir ligação do java e python
//        val captcha = resolveCaptchaElement()
//        val captchaImage = captcha.toWebElement().getScreenshotAs(OutputType.BYTES)
//        val bufferedImage = ImageIO.read(ByteArrayInputStream(captchaImage))
//            ?: throw IllegalStateException("Nao foi possivel converter a imagem do captcha")
//        val captchaText = captchaOcrService.readCaptcha(bufferedImage, dealerProperties.captcha)
//        println("Captcha OCR: $captchaText")

        BrowserRuntime.css("#IMAGE3").click()
        BrowserRuntime.css("#ext-comp-1002 > table").shouldBe(visible)
        return homePage
    }

    private fun fillCredentialsStably(credential: DealerCredential) {
        repeat(MAX_LOGIN_ATTEMPTS) {
            handleTrocarUsuarioIfPresent()
            typeField(USUARIO_SELECTOR, credential.username)
            typeField(SENHA_SELECTOR, credential.password)
            if (isFormConsistent(credential)) {
                return
            }
        }
        throw IllegalStateException(
            "Formulario de login ficou inconsistente apos $MAX_LOGIN_ATTEMPTS tentativas " +
                "(usuario/senha nao confirmaram seus valores).",
        )
    }

    /**
     * Digita [value] em [selector], dispara o blur (para acionar o onblur/onchange do
     * GeneXus) e espera o AJAX de login assentar antes de prosseguir.
     */
    private fun typeField(selector: String, value: String) {
        val field = BrowserRuntime.css(selector).shouldBe(visible, enabled)
        field.setValue(value)
        blur(field)
        settleLoginAjax()
    }

    /** Re-le ambos os campos e confirma que cada um contem exatamente o valor esperado. */
    private fun isFormConsistent(credential: DealerCredential): Boolean {
        val usuario = currentValue(USUARIO_SELECTOR)
        val senha = currentValue(SENHA_SELECTOR)
        return usuario.equals(credential.username, ignoreCase = true) && senha == credential.password
    }

    /** Quando o login abre com um usuario lembrado, o botao "Trocar usuario" fica visivel. */
    private fun handleTrocarUsuarioIfPresent() {
        val botao = BrowserRuntime.css("#TROCAUSUARIO")
        if (botao.exists() && botao.isDisplayed) {
            botao.click()
            settleLoginAjax()
        }
    }

    private fun currentValue(selector: String): String {
        return BrowserRuntime.css(selector).getValue()?.trim().orEmpty()
    }

    private fun blur(element: SelenideElement) {
        BrowserRuntime.executeJavaScript<Any>("arguments[0].blur();", element)
    }

    private fun settleLoginAjax(
        timeoutSeconds: Long = 10,
        appearWindowMs: Long = 400,
        pollMs: Long = 50,
    ) {
        val deadline = System.currentTimeMillis() + appearWindowMs
        var appeared = isLoginAjaxVisible()
        while (!appeared && System.currentTimeMillis() < deadline) {
            BrowserRuntime.sleep(pollMs)
            appeared = isLoginAjaxVisible()
        }
        if (appeared) {
            BrowserRuntime.css("#gx_ajax_notification").shouldBe(hidden, Duration.ofSeconds(timeoutSeconds))
        }
    }

    private fun isLoginAjaxVisible(): Boolean {
        return BrowserRuntime.executeJavaScript<Boolean>(
            """
            const el = document.getElementById('gx_ajax_notification');
            if (!el) return false;
            const style = window.getComputedStyle(el);
            return style.display !== 'none' && style.visibility !== 'hidden' && el.offsetParent !== null;
            """.trimIndent(),
        ) == true
    }

    private fun resolveCaptchaElement(): SelenideElement {
        val captchaContainer = BrowserRuntime.css(dealerProperties.captcha.imageSelector).shouldBe(visible)
        return captchaContainer.find("img").takeIf { it.exists() } ?: captchaContainer
    }

    companion object {
        private const val MAX_LOGIN_ATTEMPTS = 3
        private const val USUARIO_SELECTOR = "#vUSUARIO_IDENTIFICADORALTERNATIVO"
        private const val SENHA_SELECTOR = "#vUSUARIOSENHA_SENHA"
    }
}
