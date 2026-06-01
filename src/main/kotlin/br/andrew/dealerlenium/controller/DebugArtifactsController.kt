package br.andrew.dealerlenium.controller

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

@Controller
@RequestMapping("/api/debug/artifacts")
class DebugArtifactsController {
    private val debugDirectory: Path = Path.of("build", "reports", "dealer-debug")
    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    @GetMapping(produces = [MediaType.TEXT_HTML_VALUE])
    @ResponseBody
    fun listArtifacts(): String {
        val files = if (debugDirectory.isDirectory()) {
            Files.list(debugDirectory).use { paths ->
                paths
                    .filter { it.isRegularFile() }
                    .sorted(compareByDescending<Path> { Files.getLastModifiedTime(it).toInstant() }.thenBy { it.name })
                    .map { file ->
                        ArtifactFile(
                            name = file.name,
                            size = runCatching { file.fileSize() }.getOrDefault(0L),
                            lastModified = runCatching { Files.getLastModifiedTime(file).toInstant() }.getOrDefault(Instant.EPOCH),
                            extension = file.extension.lowercase(),
                        )
                    }
                    .toList()
            }
        } else {
            emptyList()
        }

        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"pt-BR\">")
            appendLine("<head>")
            appendLine("<meta charset=\"UTF-8\">")
            appendLine("<title>Dealer Debug Artifacts</title>")
            appendLine("<style>")
            appendLine("body { font-family: sans-serif; margin: 24px; }")
            appendLine("table { border-collapse: collapse; width: 100%; }")
            appendLine("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }")
            appendLine("th { background: #f5f5f5; }")
            appendLine("code { background: #f3f3f3; padding: 2px 4px; }")
            appendLine("a { text-decoration: none; }")
            appendLine("</style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("<h1>Dealer Debug Artifacts</h1>")
            appendLine("<p>Diretorio: <code>${escapeHtml(debugDirectory.toAbsolutePath().toString())}</code></p>")
            appendLine("<p>Endpoint: <code>/api/debug/artifacts</code></p>")

            if (files.isEmpty()) {
                appendLine("<p>Nenhum arquivo encontrado.</p>")
            } else {
                appendLine("<table>")
                appendLine("<thead>")
                appendLine("<tr><th>Arquivo</th><th>Modificado em</th><th>Tamanho</th><th>Links</th></tr>")
                appendLine("</thead>")
                appendLine("<tbody>")
                files.forEach { file ->
                    val encodedName = encodePathSegment(file.name)
                    val viewUrl = "./files/$encodedName"
                    val downloadUrl = "./files/$encodedName?download=true"
                    appendLine(
                        "<tr>" +
                            "<td><code>${escapeHtml(file.name)}</code></td>" +
                            "<td>${escapeHtml(timestampFormatter.format(file.lastModified))}</td>" +
                            "<td>${escapeHtml(formatSize(file.size))}</td>" +
                            "<td>" +
                            "<a href=\"$viewUrl\">abrir</a> | " +
                            "<a href=\"$downloadUrl\">baixar</a>" +
                            "</td>" +
                            "</tr>"
                    )
                }
                appendLine("</tbody>")
                appendLine("</table>")
            }

            appendLine("</body>")
            appendLine("</html>")
        }
    }

    @GetMapping("/files/{fileName:.+}")
    fun downloadArtifact(
        @PathVariable fileName: String,
        @RequestParam(defaultValue = "false") download: Boolean,
    ): ResponseEntity<Resource> {
        val file = resolveArtifact(fileName)
        val resource = FileSystemResource(file)
        val headers = HttpHeaders()
        val contentType = when (file.extension.lowercase()) {
            "html" -> MediaType.TEXT_HTML
            "txt", "log" -> MediaType.TEXT_PLAIN
            else -> MediaType.APPLICATION_OCTET_STREAM
        }

        if (download) {
            headers.contentDisposition = ContentDisposition.attachment()
                .filename(file.name, StandardCharsets.UTF_8)
                .build()
        }

        return ResponseEntity.ok()
            .headers(headers)
            .contentType(contentType)
            .body(resource)
    }

    private fun resolveArtifact(fileName: String): Path {
        val candidate = debugDirectory.resolve(fileName).normalize()
        if (!candidate.startsWith(debugDirectory) || !candidate.isRegularFile()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo nao encontrado: $fileName")
        }
        return candidate
    }

    private fun encodePathSegment(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
    }

    private fun escapeHtml(value: String): String {
        return buildString(value.length) {
            value.forEach { char ->
                append(
                    when (char) {
                        '&' -> "&amp;"
                        '<' -> "&lt;"
                        '>' -> "&gt;"
                        '"' -> "&quot;"
                        '\'' -> "&#39;"
                        else -> char
                    }
                )
            }
        }
    }

    private fun formatSize(size: Long): String {
        if (size < 1024) {
            return "$size B"
        }
        if (size < 1024 * 1024) {
            return "%.1f KB".format(size / 1024.0)
        }
        return "%.1f MB".format(size / (1024.0 * 1024.0))
    }

    private data class ArtifactFile(
        val name: String,
        val size: Long,
        val lastModified: Instant,
        val extension: String,
    )
}
