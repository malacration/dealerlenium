package br.andrew.dealerlenium.browser

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object BrowserDebugArtifacts {
    private val logger = LoggerFactory.getLogger(BrowserDebugArtifacts::class.java)
    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.systemDefault())

    fun captureCurrentContext(label: String): Path? {
        return runCatching {
            val outputDir = Path.of("build", "reports", "dealer-debug")
            Files.createDirectories(outputDir)

            val filePrefix = "${timestampFormatter.format(Instant.now())}-${sanitizeLabel(label)}"
            val metadataPath = outputDir.resolve("$filePrefix.txt")
            val htmlPath = outputDir.resolve("$filePrefix.html")

            val diagnostic = collectDiagnosticData()
            Files.writeString(
                metadataPath,
                renderDiagnostic(diagnostic, htmlPath.fileName.toString()),
                Charsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )

            diagnostic.currentHtml?.let { html ->
                Files.writeString(
                    htmlPath,
                    html,
                    Charsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                )
            }

            logger.error("Diagnostico do navegador salvo em {}", metadataPath.toAbsolutePath())
            metadataPath
        }.onFailure { error ->
            logger.error("Falha ao capturar diagnostico do navegador para {}", label, error)
        }.getOrNull()
    }

    private fun collectDiagnosticData(): BrowserDiagnosticData {
        val driver = BrowserRuntime.getWebDriver()
        val driverUrl = runCatching { driver.currentUrl.orEmpty() }.getOrDefault("")
        val driverTitle = runCatching { driver.title.orEmpty() }.getOrDefault("")
        val browserContext = BrowserRuntime.executeJavaScript<Map<*, *>>(
            """
            const readLocation = (target) => {
              try {
                return target.location.href;
              } catch (error) {
                return 'ERROR: ' + ((error && error.message) ? error.message : String(error));
              }
            };
            const describeFrame = (element) => {
              if (!element) return null;
              return {
                tagName: element.tagName || '',
                id: element.id || '',
                name: element.name || '',
                src: element.getAttribute('src') || '',
                outerHTML: element.outerHTML || ''
              };
            };
            const describeAjax = () => {
              const element = document.getElementById('gx_ajax_notification');
              if (!element) return null;
              const style = window.getComputedStyle(element);
              return {
                display: style.display,
                visibility: style.visibility,
                opacity: style.opacity,
                width: element.offsetWidth,
                height: element.offsetHeight,
                text: element.innerText || '',
                ariaBusy: element.getAttribute('aria-busy') || ''
              };
            };
            return {
              windowHref: window.location.href,
              parentHref: readLocation(window.parent),
              topHref: readLocation(window.top),
              isTopWindow: window === window.top,
              documentTitle: document.title,
              readyState: document.readyState,
              referrer: document.referrer || '',
              bodyText: (document.body && document.body.innerText ? document.body.innerText : '').slice(0, 4000),
              gxErrorViewerText: Array.from(document.querySelectorAll('#gxErrorViewer, #gxErrorViewer div, .gx_ev'))
                .map((el) => (el.innerText || '').trim())
                .filter(Boolean)
                .slice(0, 20),
              confirmPanelText: Array.from(document.querySelectorAll('#DVELOP_CONFIRMPANELContainer, #DVELOP_CONFIRMPANELContainer .Body'))
                .map((el) => (el.innerText || '').trim())
                .filter(Boolean)
                .slice(0, 20),
              ajaxNotification: describeAjax(),
              activeFrame: describeFrame(window.frameElement),
              availableFrames: Array.from(document.querySelectorAll('iframe, frame')).slice(0, 20).map((element) => ({
                tagName: element.tagName || '',
                id: element.id || '',
                name: element.name || '',
                src: element.getAttribute('src') || ''
              })),
            };
            """.trimIndent(),
        ) ?: emptyMap<String, Any?>()
        val currentHtml = BrowserRuntime.executeJavaScript<String>(
            "return document.documentElement ? document.documentElement.outerHTML : '';",
        ).orEmpty()

        return BrowserDiagnosticData(
            driverUrl = driverUrl,
            driverTitle = driverTitle,
            browserContext = browserContext,
            currentHtml = currentHtml,
        )
    }

    private fun renderDiagnostic(data: BrowserDiagnosticData, htmlFileName: String): String {
        return buildString {
            appendLine("htmlFile=$htmlFileName")
            appendLine("driverUrl=${data.driverUrl}")
            appendLine("driverTitle=${data.driverTitle}")
            appendLine("htmlLength=${data.currentHtml?.length ?: 0}")
            appendLine()
            appendLine("browserContext:")
            append(renderValue(data.browserContext, 1))
        }
    }

    private fun renderValue(value: Any?, indentLevel: Int): String {
        val indent = "  ".repeat(indentLevel)
        return when (value) {
            null -> "${indent}null\n"
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    "${indent}{}\n"
                } else {
                    buildString {
                        value.forEach { (key, nestedValue) ->
                            append(indent)
                            append(key?.toString() ?: "<null>")
                            append(":")
                            when (nestedValue) {
                                null, is String, is Number, is Boolean -> {
                                    append(" ")
                                    appendLine(formatScalar(nestedValue))
                                }
                                else -> {
                                    appendLine()
                                    append(renderValue(nestedValue, indentLevel + 1))
                                }
                            }
                        }
                    }
                }
            }
            is Iterable<*> -> {
                if (!value.iterator().hasNext()) {
                    "${indent}[]\n"
                } else {
                    buildString {
                        value.forEach { item ->
                            append(indent)
                            append("-")
                            when (item) {
                                null, is String, is Number, is Boolean -> {
                                    append(" ")
                                    appendLine(formatScalar(item))
                                }
                                else -> {
                                    appendLine()
                                    append(renderValue(item, indentLevel + 1))
                                }
                            }
                        }
                    }
                }
            }
            is Array<*> -> renderValue(value.asList(), indentLevel)
            else -> "${indent}${formatScalar(value)}\n"
        }
    }

    private fun formatScalar(value: Any?): String {
        return value?.toString()?.replace("\n", "\\n") ?: "null"
    }

    private fun sanitizeLabel(label: String): String {
        val sanitized = label
            .lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-')
        return sanitized.ifBlank { "snapshot" }
    }

    private data class BrowserDiagnosticData(
        val driverUrl: String,
        val driverTitle: String,
        val browserContext: Map<*, *>,
        val currentHtml: String,
    )
}
