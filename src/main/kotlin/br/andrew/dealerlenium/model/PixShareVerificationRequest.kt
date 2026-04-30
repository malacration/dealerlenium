package br.andrew.dealerlenium.model

import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal

data class PixShareVerificationRequest(
    @field:NotBlank
    val txId: String,
    val qrCode: String? = null,
    val valor: BigDecimal? = null,
    val vencimento: String? = null,
)
