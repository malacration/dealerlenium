package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.pages.ContasReceberRegistro
import br.andrew.dealerlenium.pages.NavigationPage
import org.springframework.stereotype.Service

@Service
class TituloConsultaService(
    private val browserSessionManager: BrowserSessionManager,
    private val navigationPage: NavigationPage,
) {
    fun buscarValorTitulo(empresa: String, idTitulo: Int): ContasReceberRegistro {
        println("buscando ")
        return browserSessionManager.runInClonedStateDriver { homePage ->
            val contasReceberPage = navigationPage.goContasReceber(homePage)
            contasReceberPage.filtroEmpresa(empresa)
            contasReceberPage.getContasReceberByCodigo(idTitulo)
        }
    }

    fun buscarValorTituloDisponivel(empresa: String, idTitulo: Int): ContasReceberRegistro {
        val conta = buscarValorTitulo(empresa, idTitulo)
        require(conta.status != "Pago") { "Fatura já consta como pago" }
        return conta
    }
}
