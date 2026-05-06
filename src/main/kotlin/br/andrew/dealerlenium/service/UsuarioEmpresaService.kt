package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.infrastructure.configurations.BranchOption
import br.andrew.dealerlenium.infrastructure.configurations.EmpresaProperties
import br.andrew.dealerlenium.model.TransactionDocument
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service

@Service
class UsuarioEmpresaService(
    private val empresaProperties: EmpresaProperties,
) {
    fun getEmpresas(authentication: Authentication): List<BranchOption> {
        return empresaProperties.getBranches(getEmpresaIds(authentication))
    }

    fun getEmpresaIds(authentication: Authentication): Set<String> {
        return getRoles(authentication)
    }

    fun hasAccessToEmpresa(authentication: Authentication, empresaId: String): Boolean {
        return empresaId.trim().uppercase() in getEmpresaIds(authentication)
    }

    fun hasAccessToTransaction(authentication: Authentication, transaction: TransactionDocument): Boolean {
        return hasAccessToEmpresa(authentication, transaction.empresa)
    }

    private fun getRoles(authentication: Authentication): Set<String> {
        val roles = authentication.authorities
            .map { it.authority.trim().uppercase() }
            .filter { it.isNotBlank() }
            .toSet()

        return roles
    }
}
