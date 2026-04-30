package br.andrew.dealerlenium.model

data class Cliente(
    val codigo: Int,
    val nome: String?,
    val nomeFantasia: String?,
    val cpfCnpj: String?,
    val municipio: String?,
    val uf: String?,
    val ativo: Boolean,
    val codigoMontadora: String?,
)
