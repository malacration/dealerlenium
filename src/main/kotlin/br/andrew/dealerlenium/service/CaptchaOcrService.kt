package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.CaptchaProperties
import net.sourceforge.tess4j.Tesseract
import net.sourceforge.tess4j.TesseractException
import org.springframework.stereotype.Service
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path

@Service
class CaptchaOcrService {

    fun readCaptcha(image: BufferedImage, config: CaptchaProperties): String {
        val datapath = resolveDatapath(config)
        val language = resolveLanguage(config, datapath)
        val tesseract = Tesseract().apply {
            setDatapath(datapath.toString())
            setLanguage(language)
            setPageSegMode(config.pageSegMode)
            config.charWhitelist
                ?.takeIf { it.isNotBlank() }
                ?.let { setVariable("tessedit_char_whitelist", it) }
        }

        return try {
            tesseract.doOCR(preprocess(image)).trim()
        } catch (exception: TesseractException) {
            throw IllegalStateException("Falha ao executar OCR do captcha", exception)
        }
    }

    private fun resolveDatapath(config: CaptchaProperties): Path {
        val configuredPath = config.tessdataPath
            ?.takeIf { it.isNotBlank() }
            ?.let(Path::of)

        val resolvedPath = configuredPath
            ?: DEFAULT_TESSDATA_PATHS.firstOrNull { Files.isDirectory(it) }

        return resolvedPath?.takeIf { Files.isDirectory(it) }
            ?: throw IllegalStateException(
                "Nao encontrei a pasta tessdata. Configure dealer.captcha.tessdata-path ou a variavel DEALER_TESSDATA_PATH. " +
                    "Caminhos verificados: ${DEFAULT_TESSDATA_PATHS.joinToString()}",
            )
    }

    private fun resolveLanguage(config: CaptchaProperties, datapath: Path): String {
        val requestedLanguage = config.language.trim()
        val requestedModel = datapath.resolve("$requestedLanguage.traineddata")
        if (Files.isRegularFile(requestedModel)) {
            return requestedLanguage
        }

        val englishModel = datapath.resolve("eng.traineddata")
        if (Files.isRegularFile(englishModel)) {
            println(
                "Captcha OCR: idioma '$requestedLanguage' nao encontrado em $datapath. " +
                    "Usando fallback 'eng'.",
            )
            return "eng"
        }

        throw IllegalStateException(
            "Nao encontrei o arquivo de idioma '$requestedLanguage' em $datapath. " +
                "Esperado: $requestedModel. Instale o pacote de idioma do Tesseract ou ajuste DEALER_TESSDATA_PATH.",
        )
    }

    private fun preprocess(image: BufferedImage): BufferedImage {
        val scaledWidth = (image.width * 2).coerceAtLeast(1)
        val scaledHeight = (image.height * 2).coerceAtLeast(1)
        val grayscale = BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_BYTE_GRAY)
        val graphics = grayscale.createGraphics()

        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.drawImage(image, 0, 0, scaledWidth, scaledHeight, null)
        } finally {
            graphics.dispose()
        }

        return threshold(grayscale)
    }

    private fun threshold(image: BufferedImage): BufferedImage {
        val binary = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_BINARY)
        val graphics = binary.createGraphics()

        try {
            graphics.background = Color.WHITE
            graphics.clearRect(0, 0, binary.width, binary.height)

            for (x in 0 until image.width) {
                for (y in 0 until image.height) {
                    val gray = Color(image.getRGB(x, y)).red
                    val rgb = if (gray < 160) Color.BLACK.rgb else Color.WHITE.rgb
                    binary.setRGB(x, y, rgb)
                }
            }
        } finally {
            graphics.dispose()
        }

        return binary
    }

    companion object {
        private val DEFAULT_TESSDATA_PATHS = listOf(
            Path.of("tessdata"),
            Path.of("src/main/resources/tessdata"),
            Path.of("/usr/share/tesseract-ocr/5/tessdata"),
            Path.of("/usr/share/tessdata"),
            Path.of("/usr/local/share/tessdata"),
            Path.of("/opt/homebrew/share/tessdata"),
        )
    }
}
