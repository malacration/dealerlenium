package br.andrew.dealerlenium.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class TransactionStatus(
    @get:JsonValue
    val value: String,
) {
    CRIADO("Criado"),
    PAGO("Pago"),
    EXPIRADO("Expirado"),
    ERRO_BAIXA("Erro na baixa");

    companion object {
        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun from(rawValue: String): TransactionStatus {
            val normalized = rawValue.trim()
            require(normalized.isNotEmpty()) { "Status da transacao nao pode ser vazio" }

            return entries.firstOrNull { it.value.equals(normalized, ignoreCase = true) }
                ?: entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
                ?: when (normalized.lowercase()) {
                    "erro baixa" -> ERRO_BAIXA
                    "erro_baixa" -> ERRO_BAIXA
                    "errodabaixa" -> ERRO_BAIXA
                    else -> throw IllegalArgumentException("Status da transacao desconhecido: $rawValue")
                }
        }
    }
}
