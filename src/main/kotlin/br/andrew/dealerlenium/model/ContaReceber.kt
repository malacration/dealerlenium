package br.andrew.dealerlenium.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class SituacaoContaReceber { ABERTO, PARCIAL, PAGO, CANCELADO }

data class ContaReceber(
    val tituloId: String,
    val empresaId: String,
    val empresaNome: String,
    val documento: String,
    val parcela: String?,
    val clienteId: String,
    val clienteNome: String,
    val clienteDocumento: String?,
    val emissao: LocalDate?,
    val vencimento: LocalDate,
    val valorOriginal: BigDecimal,
    val saldoAberto: BigDecimal,
    val situacao: SituacaoContaReceber,
    val podeGerarPix: Boolean,
)

data class ContasReceberPagina(val itens: List<ContaReceber>, val total: Int)
data class ClienteSelecao(val id: String, val nome: String, val documento: String?)
data class ClientesSelecaoPagina(val itens: List<ClienteSelecao>, val total: Int)

data class ContasReceberFiltro(
    val pagina: Int = 0,
    val tamanho: Int = 20,
    val busca: String = "",
    val empresaId: String? = null,
    val situacao: SituacaoContaReceber? = null,
    val vencimentoDe: LocalDate? = null,
    val vencimentoAte: LocalDate? = null,
    val clienteId: String? = null,
    val lancamento: Int? = null,
    val documento: Int? = null,
    val parcela: Int? = null,
) {
    fun validar(consultaClientes: Boolean = false) {
        require(lancamento == null || lancamento > 0) { "Numero do lancamento deve ser positivo." }
        require(documento == null || documento > 0) { "Numero do documento deve ser positivo." }
        require(parcela == null || parcela > 0) { "Numero da parcela deve ser positivo." }
        require(clienteId.isNullOrBlank() || (clienteId.toIntOrNull()?.let { it > 0 } == true)) { "Codigo do cliente invalido." }
        require(consultaClientes || !clienteId.isNullOrBlank() || !empresaId.isNullOrBlank()) { "Selecione uma filial para consultar os titulos." }
        require(consultaClientes || !clienteId.isNullOrBlank() || (vencimentoDe != null && vencimentoAte != null)) { "Informe as datas inicial e final de vencimento." }
        require(pagina >= 0) { "Pagina deve ser maior ou igual a zero." }
        require(tamanho in setOf(20, 50, 100)) { "Tamanho deve ser 20, 50 ou 100." }
        require(busca.length <= 200) { "Busca deve ter no maximo 200 caracteres." }
        require(vencimentoDe == null || vencimentoAte == null || !vencimentoDe.isAfter(vencimentoAte)) {
            "Vencimento inicial deve ser anterior ou igual ao final."
        }
        require(consultaClientes || !clienteId.isNullOrBlank() || ChronoUnit.DAYS.between(requireNotNull(vencimentoDe), requireNotNull(vencimentoAte)) < 30) {
            "O periodo de vencimento deve ter no maximo 30 dias, incluindo as datas inicial e final."
        }
    }
}
