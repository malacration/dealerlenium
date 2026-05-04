package br.andrew.dealerlenium.infrastructure.configurations

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "empresa")
data class EmpresaProperties(
    val configuracoes: Map<String, EmpresaConfig> = emptyMap(),
) {
    fun getBranches(): List<BranchOption> {
        return listEmpresas()
            .map { empresa ->
                BranchOption(
                    id = empresa.id,
                    label = empresa.descricao?.trim().takeUnless { it.isNullOrEmpty() } ?: empresa.id.replace('_', ' '),
                )
            }
    }

    fun listEmpresas(): List<Empresa> {
        return configuracoes.entries
            .sortedBy { it.key }
            .map { (id, empresa) -> empresa.toEmpresa(id) }
    }

    fun getEmpresa(empresaId: String): Empresa? {
        val chave = empresaId.trim().uppercase()
        return configuracoes[chave]?.toEmpresa(chave)
    }

    fun getEmpresaOrThrow(empresaId: String): Empresa {
        return getEmpresa(empresaId)
            ?: throw IllegalArgumentException("Empresa '$empresaId' nao esta configurada em empresa.configuracoes")
    }

    fun getEmpresaPadrao(): String {
        return configuracoes.keys.firstOrNull()
            ?: throw IllegalArgumentException("Nenhuma empresa foi configurada em empresa.configuracoes")
    }

    fun getCnpjByEmpresa(empresaId: String): String = getEmpresaOrThrow(empresaId).cnpj

    fun getAdiantamentoByEmpresa(empresaId: String): AdiantamentoProperties =
        getEmpresaOrThrow(empresaId).adiantamento

    fun getDescricaoByEmpresaOrNull(empresaId: String): String? {
        return getEmpresa(empresaId)?.descricao?.trim().takeUnless { it.isNullOrEmpty() }
    }
}

data class EmpresaConfig(
    val cnpj: String,
    val descricao: String? = null,
    val marca: String,
    val chavePix: String? = null,
    val titular: String? = null,
    val adiantamento: AdiantamentoProperties,
) {
    fun toEmpresa(id: String): Empresa {
        return Empresa(
            id = id,
            cnpj = cnpj,
            descricao = descricao,
            marca = marca,
            chavePix = chavePix,
            titular = titular,
            adiantamento = adiantamento,
        )
    }
}

data class Empresa(
    val id: String,
    val cnpj: String,
    val descricao: String? = null,
    val marca: String,
    val chavePix: String? = null,
    val titular: String? = null,
    val adiantamento: AdiantamentoProperties,
)

data class BranchOption(
    val id: String,
    val label: String,
)
