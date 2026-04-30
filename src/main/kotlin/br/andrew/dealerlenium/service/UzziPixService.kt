package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.infrastructure.configurations.EmpresaProperties
import br.andrew.dealerlenium.infrastructure.configurations.UzziPixProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime

@Service
class UzziPixService(
    private val properties: UzziPixProperties,
    private val empresaProperties: EmpresaProperties,
    restClientBuilder: RestClient.Builder,
) {
    private val restClient = restClientBuilder
        .baseUrl(properties.baseUrl)
        .build()

    fun createPixByEmpresa(
        empresa: String,
        externalIdentifier: String,
        amount: BigDecimal,
    ): DataRetonroPixQrCode {
        val cnpj = empresaProperties.getCnpjByEmpresa(empresa)
        return createPixByCnpj(RequestPix(cnpj,externalIdentifier, amount))
    }

    fun createPixByCnpj(
        request: RequestPix,
    ): DataRetonroPixQrCode {
        try {
            return restClient.post()
                .uri("/cnpj/${request.cnpj}")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(DataRetonroPixQrCode::class.java)
                ?: throw IllegalStateException("UzziPix retornou resposta vazia")
        } catch (ex: RestClientResponseException) {
            throw IllegalStateException(
                "Falha ao criar PIX no UzziPix. Status=${ex.statusCode.value()} body=${ex.responseBodyAsString}",
                ex,
            )
        } catch (ex: RestClientException) {
            throw IllegalStateException("Falha ao comunicar com o middleware de pix", ex)
        }
    }

    fun verifica(empresa: String, reference: String): UzziPixTransactionStatus {
        val cnpj = empresaProperties.getCnpjByEmpresa(empresa)
        try {
            val response = restClient.get()
                .uri("cnpj/${cnpj}/transaction/${reference}")
                .retrieve()
                .body(JsonNode::class.java)
                ?: throw IllegalStateException("UzziPix retornou resposta vazia")

            return response.toTransactionStatus()
        } catch (ex: RestClientResponseException) {
            throw IllegalStateException(
                "Falha ao consultar PIX no UzziPix. Status=${ex.statusCode.value()} body=${ex.responseBodyAsString}",
                ex,
            )
        } catch (ex: RestClientException) {
            throw IllegalStateException("Falha ao comunicar com o middleware de pix", ex)
        }
    }

}

data class UzziPixTransactionStatus(
    val txId: String? = null,
    val originalAmount: BigDecimal? = null,
    val paid: Boolean? = null,
    val paymentDate: OffsetDateTime? = null,
    val paymentType: String? = null,
    val receivedAmount: BigDecimal? = null,
    val status: String? = null,
) {
    fun resolvedPaid(): Boolean? {
        paid?.let { return it }

        val normalizedStatus = status?.trim()?.lowercase()
            ?: return null

        return when {
            normalizedStatus.contains("pago") || normalizedStatus.contains("paid") ||
                normalizedStatus.contains("recebido") || normalizedStatus.contains("completed") -> true
            normalizedStatus.contains("aguard") || normalizedStatus.contains("pending") ||
                normalizedStatus.contains("aberto") || normalizedStatus.contains("open") ||
                normalizedStatus.contains("nao pago") || normalizedStatus.contains("unpaid") -> false
            else -> null
        }
    }
}

private fun JsonNode.toTransactionStatus(): UzziPixTransactionStatus {
    val payload = this["data"]?.takeIf { it.isObject } ?: this

    return UzziPixTransactionStatus(
        txId = firstText(payload, this, "txId", "txid", "reference"),
        originalAmount = firstDecimal(payload, this, "originalAmount", "amount", "original_value"),
        paid = firstBoolean(payload, this, "paid", "isPaid"),
        paymentDate = firstDate(payload, this, "paymentDate", "paidAt", "paymentTimestamp"),
        paymentType = firstText(payload, this, "paymentType", "type"),
        receivedAmount = firstDecimal(payload, this, "receivedAmount", "amountPaid", "paidAmount", "received_value"),
        status = firstText(payload, this, "status", "situation", "situacao"),
    )
}

private fun firstText(primary: JsonNode, fallback: JsonNode, vararg keys: String): String? {
    for (key in keys) {
        primary[key]?.takeIf { !it.isNull }?.asText()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }

    if (primary !== fallback) {
        for (key in keys) {
            fallback[key]?.takeIf { !it.isNull }?.asText()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
    }

    return null
}

private fun firstBoolean(primary: JsonNode, fallback: JsonNode, vararg keys: String): Boolean? {
    for (key in keys) {
        primary[key]?.takeIf { !it.isNull }?.let { node ->
            return when {
                node.isBoolean -> node.booleanValue()
                node.isTextual -> node.asText().trim().lowercase().let { it == "true" || it == "1" }
                node.isNumber -> node.intValue() != 0
                else -> null
            }
        }
    }

    if (primary !== fallback) {
        for (key in keys) {
            fallback[key]?.takeIf { !it.isNull }?.let { node ->
                return when {
                    node.isBoolean -> node.booleanValue()
                    node.isTextual -> node.asText().trim().lowercase().let { it == "true" || it == "1" }
                    node.isNumber -> node.intValue() != 0
                    else -> null
                }
            }
        }
    }

    return null
}

private fun firstDecimal(primary: JsonNode, fallback: JsonNode, vararg keys: String): BigDecimal? {
    for (key in keys) {
        primary[key]?.takeIf { !it.isNull }?.let { node ->
            return when {
                node.isNumber -> node.decimalValue()
                node.isTextual -> node.asText().trim().replace(",", ".").toBigDecimalOrNull()
                else -> null
            }
        }
    }

    if (primary !== fallback) {
        for (key in keys) {
            fallback[key]?.takeIf { !it.isNull }?.let { node ->
                return when {
                    node.isNumber -> node.decimalValue()
                    node.isTextual -> node.asText().trim().replace(",", ".").toBigDecimalOrNull()
                    else -> null
                }
            }
        }
    }

    return null
}

private fun firstDate(primary: JsonNode, fallback: JsonNode, vararg keys: String): OffsetDateTime? {
    for (key in keys) {
        primary[key]?.takeIf { !it.isNull }?.asText()?.trim()?.takeIf { it.isNotEmpty() }?.let { return parseOffsetDateTime(it) }
    }

    if (primary !== fallback) {
        for (key in keys) {
            fallback[key]?.takeIf { !it.isNull }?.asText()?.trim()?.takeIf { it.isNotEmpty() }?.let { return parseOffsetDateTime(it) }
        }
    }

    return null
}

private fun parseOffsetDateTime(value: String): OffsetDateTime? {
    return runCatching { OffsetDateTime.parse(value) }
        .recoverCatching { Instant.parse(value).atOffset(java.time.ZoneOffset.UTC) }
        .getOrNull()
}

data class RequestPix(
    val cnpj: String,
    val externalIdentifier: String,
    val amount: BigDecimal,
    val expiration: Int = 3600,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DataRetonroPixQrCode(val data: RetonroPixQrCode)

@JsonIgnoreProperties(ignoreUnknown = true)
class RetonroPixQrCode(
    val textContent: String,
    val link: String,
    val reference: String,
    @JsonAlias("generatedAt", "createdAt", "geradoEm")
    val generatedAt: OffsetDateTime? = null,
    @JsonAlias("expiresAt", "expirationAt", "expiraEm")
    val expiresAt: OffsetDateTime? = null,
)
