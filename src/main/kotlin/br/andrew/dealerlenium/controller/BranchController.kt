package br.andrew.dealerlenium.controller

import br.andrew.dealerlenium.infrastructure.configurations.BranchOption
import br.andrew.dealerlenium.infrastructure.configurations.EmpresaProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/branch")
class BranchController(
    private val empresaProperties: EmpresaProperties,
) {

    @GetMapping
    fun listBranches(): List<BranchOption> = empresaProperties.getBranches()
}
