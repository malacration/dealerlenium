package br.andrew.dealerlenium.model

import java.math.BigDecimal

data class TransactionParameters(
    val idCliente: String,
    val branchId: String,
    val valor: BigDecimal,
    val departamentoCod: Int? = null,
    val tipoFichaRazaoCod: Int? = null,
)
