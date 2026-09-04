package br.andrew.dealerlenium.service

/**
 * Alcance de filiais de uma sessao do pool, derivado de `dealer.credentials[i].empresa`.
 *
 * - [Dedicada]: o login so atende aquela filial e fica travado nela desde o login.
 * - [Todas]: o login atende qualquer filial (`empresa: all`).
 * - [Geral]: o login atende todas as filiais, exceto as que ja tem um login dedicado.
 */
sealed interface DealerSessionScope {

    data class Dedicada(val empresaId: String) : DealerSessionScope

    data object Todas : DealerSessionScope

    data object Geral : DealerSessionScope

    companion object {
        const val TODAS = "ALL"

        fun of(empresa: String): DealerSessionScope {
            val valor = normalizar(empresa)
            return when {
                valor.isEmpty() -> Geral
                valor == TODAS -> Todas
                else -> Dedicada(valor)
            }
        }

        /** Mesma normalizacao usada por `EmpresaProperties.getEmpresa`. */
        fun normalizar(empresa: String?): String = empresa?.trim()?.uppercase().orEmpty()

        /**
         * Filial alvo de uma operacao. `null` quando a operacao nao sabe a filial
         * (ou pede explicitamente `all`, como a busca de titulo no grid).
         */
        fun alvoDe(empresa: String?): String? =
            normalizar(empresa).takeIf { it.isNotEmpty() && it != TODAS }
    }
}

/**
 * Roteamento de uma requisicao para os escopos disponiveis no pool.
 *
 * Uma filial com login dedicado nunca cai no pool geral: se a sessao dedicada
 * estiver ocupada, a requisicao espera ela liberar.
 */
class DealerSessionRouting(escopos: List<DealerSessionScope>) {

    val filiaisDedicadas: Set<String> = escopos
        .filterIsInstance<DealerSessionScope.Dedicada>()
        .map { it.empresaId }
        .toSet()

    private val escoposDisponiveis: List<DealerSessionScope> = escopos

    /** [alvo] nulo = operacao sem filial: so os logins gerais e os `all` atendem. */
    fun aceita(escopo: DealerSessionScope, alvo: String?): Boolean = when (escopo) {
        is DealerSessionScope.Dedicada -> alvo != null && escopo.empresaId == alvo
        DealerSessionScope.Todas -> true
        DealerSessionScope.Geral -> alvo == null || alvo !in filiaisDedicadas
    }

    /** Preferencia: dedicada ao alvo, depois geral, depois `all` (capacidade sobressalente). */
    fun <S> escolher(livres: List<S>, escopoDe: (S) -> DealerSessionScope, alvo: String?): S? {
        val candidatas = livres.filter { aceita(escopoDe(it), alvo) }
        if (candidatas.isEmpty()) {
            return null
        }
        return candidatas.minByOrNull { prioridade(escopoDe(it)) }
    }

    /** Falso quando nenhuma credencial configurada consegue atender [alvo] (falha rapido). */
    fun atendida(alvo: String?): Boolean = escoposDisponiveis.any { aceita(it, alvo) }

    private fun prioridade(escopo: DealerSessionScope): Int = when (escopo) {
        is DealerSessionScope.Dedicada -> 0
        DealerSessionScope.Geral -> 1
        DealerSessionScope.Todas -> 2
    }
}
