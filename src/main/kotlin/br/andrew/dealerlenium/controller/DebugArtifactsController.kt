package br.andrew.dealerlenium.controller

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
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

@RestController
@RequestMapping("/api/debug/artifacts")
class DebugArtifactsController {
    private val debugDirectory: Path = Path.of("build", "reports", "dealer-debug")
    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    @GetMapping
    fun listArtifacts(): DebugArtifactsResponse {
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

        return DebugArtifactsResponse(
            directory = debugDirectory.toAbsolutePath().toString(),
            endpoint = "/api/debug/artifacts",
            totalFiles = files.size,
            files = files.map { file ->
                val encodedName = encodePathSegment(file.name)
                DebugArtifactItemResponse(
                    name = file.name,
                    extension = file.extension,
                    sizeBytes = file.size,
                    sizeLabel = formatSize(file.size),
                    lastModified = timestampFormatter.format(file.lastModified),
                    viewUrl = "./files/$encodedName",
                    downloadUrl = "./files/$encodedName?download=true",
                )
            },
        )
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

data class DebugArtifactsResponse(
    val directory: String,
    val endpoint: String,
    val totalFiles: Int,
    val files: List<DebugArtifactItemResponse>,
)

data class DebugArtifactItemResponse(
    val name: String,
    val extension: String,
    val sizeBytes: Long,
    val sizeLabel: String,
    val lastModified: String,
    val viewUrl: String,
    val downloadUrl: String,
)
