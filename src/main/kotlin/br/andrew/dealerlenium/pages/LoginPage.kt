package br.andrew.dealerlenium.pages

import br.andrew.dealerlenium.DealerProperties
import br.andrew.dealerlenium.browser.BrowserRuntime
import br.andrew.dealerlenium.service.CaptchaOcrService
import com.codeborne.selenide.Condition.visible
import com.codeborne.selenide.SelenideElement
import org.openqa.selenium.OutputType
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

@Component
class LoginPage(
    private val dealerProperties: DealerProperties,
    private val captchaOcrService: CaptchaOcrService,
    private val homePage: HomePage,
) {
    fun login(): HomePage {
        BrowserRuntime.open(dealerProperties.loginUrl)
        if (dealerProperties.fullscreen && !dealerProperties.headless) {
            BrowserRuntime.getWebDriver().manage().window().maximize()
        }
        BrowserRuntime.css("#vUSUARIO_IDENTIFICADORALTERNATIVO").shouldBe(visible).setValue(dealerProperties.username)
        BrowserRuntime.css("#vUSUARIOSENHA_SENHA").shouldBe(visible).setValue(dealerProperties.password)
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

    private fun resolveCaptchaElement(): SelenideElement {
        val captchaContainer = BrowserRuntime.css(dealerProperties.captcha.imageSelector).shouldBe(visible)
        return captchaContainer.find("img").takeIf { it.exists() } ?: captchaContainer
    }
}
