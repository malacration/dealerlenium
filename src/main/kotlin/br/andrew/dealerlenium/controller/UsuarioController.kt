package br.andrew.dealerlenium.controller

import br.andrew.dealerlenium.infrastructure.configurations.BranchOption
import br.andrew.dealerlenium.service.UsuarioEmpresaService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/usuario")
class UsuarioController(
    private val usuarioEmpresaService: UsuarioEmpresaService,
) {
    @GetMapping("/empresas")
    fun getEmpresas(authentication: Authentication): List<BranchOption> {
        return usuarioEmpresaService.getEmpresas(authentication)
    }
}

