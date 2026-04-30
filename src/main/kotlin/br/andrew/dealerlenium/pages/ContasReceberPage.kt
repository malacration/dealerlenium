package br.andrew.dealerlenium.pages

import br.andrew.dealerlenium.DealerProperties
import br.andrew.dealerlenium.browser.BrowserRuntime
import com.codeborne.selenide.Condition.visible
import org.springframework.stereotype.Component

@Component
class ContasReceberPage(
    private val dealerProperties: DealerProperties,
    private val homePage: HomePage,
    private val frameSwitcher: FrameSwitcher,
    private val filtroPage: FiltroPage
) : AuthenticatedPage {
    override fun goHome(): HomePage = homePage.goHome()

    fun filtroEmpresa(empresaNome : String): ContasReceberPage {
        if (!frameSwitcher.frameBySrcExists("wp_filtroselecao.aspx")) {
            BrowserRuntime.css("#IMAGE_FILTROSELECAO").shouldBe(visible).click()
        }
        frameSwitcher.switchToFrameBySrc("wp_filtroselecao.aspx")

        if(empresaNome == "all"){
            filtroPage.selecionarAll("Empresas")
        }else{
            BrowserRuntime.css("#IMGLIMPAR").shouldBe(visible).click()
            waitAjaxLoadingToFinish()
            filtroPage.selecionarFiltro("Empresas",empresaNome)
        }

        filtroPage.selecionarFiltro("Tipo de Título a Receber",dealerProperties.tipoTitulo)

        this.frameSwitcher.afterCloseCurrentFrameSwitchToParent({
            BrowserRuntime.css("#IMGCONFIRMAR").shouldBe(visible).click()
        })
        return this
    }


    fun getContasReceberByCodigo(id: Int): ContasReceberRegistro {
        BrowserRuntime.css("#vTITULO_CODIGO").shouldBe(visible).setValue(id.toString())
        BrowserRuntime.css("#BTNCONSULTAR").shouldBe(visible).click()
        this.waitAjaxLoadingToFinish()

        if(!SelenideElementHelper.exists("#span_vGRID_TITULO_VALOR_0001"))
            throw Exception("Registor não existe")

        return ContasReceberRegistro(
            tmpTitulosUsuario = SelenideElementHelper.textOrNull("#span_vTMPTITULOS_USUARIO_0001"),
            tmpTituloCodigo = SelenideElementHelper.textOrNull("#span_vTMPTITULO_CODIGO_0001"),
            updateHabilitado = SelenideElementHelper.isEnabled("#vUPDATE_0001"),
            selecionado = SelenideElementHelper.isChecked("input[name='vGRID1_ISSELECT_0001']"),
            movimentoHabilitado = SelenideElementHelper.isEnabled("#vBMPMOVIMENTO_0001"),
            displayHabilitado = SelenideElementHelper.isEnabled("#vDISPLAY_0001"),
            cancelarHabilitado = SelenideElementHelper.isEnabled("#vBMPCANCELAR_0001"),
            transferenciaCredorHabilitado = SelenideElementHelper.isEnabled("#vTRANSFERENCIACREDOR_0001"),
            pagamentoEletronicoHabilitado = SelenideElementHelper.isEnabled("#vPGTOELET_0001"),
            impressaoHabilitado = SelenideElementHelper.isEnabled("#vIMPRESSAOOBRIGACAO_0001"),
            pagamentoNoDiaHabilitado = SelenideElementHelper.isEnabled("#vIMGPAGTODIA_0001"),
            contaGerencialHabilitada = SelenideElementHelper.exists("#vCONTAGERENCIAL_0001"),
            tituloMovimentoFinanceiro = SelenideElementHelper.textOrNull("#span_vGRID_TITULO_MOVIMENTOFINANCEIRO_0001"),
            empresaCod = SelenideElementHelper.textOrNull("#span_vGRID_TITULO_EMPRESACOD_0001"),
            empresa = SelenideElementHelper.textOrNull("#span_vGRID_TITULO_EMPRESANOM_0001"),
            pessoaCod = SelenideElementHelper.textOrNull("#span_vGRID_TITULO_PESSOACOD_0001"),
            sacado = SelenideElementHelper.textOrNull("#span_vGRID_TITULO_PESSOANOM_0001"),
            lancamento = SelenideElementHelper.textOrNull("#span_vGRID_TITULO_CODIGO_0001"),
            numeroParcela = SelenideElementHelper.textOrNull("#span_vGRID_TITULO_NUMEROPARCELA_0001"),
            tipoTitulo = SelenideElementHelper.textOrNull("#span_vGRID_TITULO_TIPOTITULODES_0001"),
            emissao = SelenideElementHelper.textOrNull("#span_vGRID_TITULO_DATAEMISSAO_0001"),
            entrada = SelenideElementHelper.textOrNull("#span_vGRID_TITULO_DATAENTRADA_0001"),
            vencimento = SelenideElementHelper.textOrNull("#span_vGRID_TITULO_DATAVENCIMENTO_0001"),
            tituloAtrasoHabilitado = SelenideElementHelper.isEnabled("#vTITULOATRASO_0001"),
            pagamento = SelenideElementHelper.textOrNull("#span_vGRID_TITULO_DATAPAGAMENTO_0001"),
            valor = SelenideElementHelper.textOrNull("#span_vGRID_TITULO_VALOR_0001"),
            aAprovar = SelenideElementHelper.textOrNull("#span_vGRID_AAPROVAR_0001"),
            saldo = SelenideElementHelper.textOrNull("#span_vGRID_TITULO_SALDO_0001"),
            observacao = SelenideElementHelper.textOrNull("#span_vGRID_TITULO_OBSERVACAO_0001"),
            status = SelenideElementHelper.textOrNull("#span_vGRID_TITULO_STATUS_0001"),
        )
    }
}

data class ContasReceberRegistro(
    val tmpTitulosUsuario: String?,
    val tmpTituloCodigo: String?,
    val updateHabilitado: Boolean,
    val selecionado: Boolean,
    val movimentoHabilitado: Boolean,
    val displayHabilitado: Boolean,
    val cancelarHabilitado: Boolean,
    val transferenciaCredorHabilitado: Boolean,
    val pagamentoEletronicoHabilitado: Boolean,
    val impressaoHabilitado: Boolean,
    val pagamentoNoDiaHabilitado: Boolean,
    val contaGerencialHabilitada: Boolean,
    val tituloMovimentoFinanceiro: String?,
    val empresaCod: String?,
    val empresa: String?,
    val pessoaCod: String?,
    val sacado: String?,
    val lancamento: String?,
    val numeroParcela: String?,
    val tipoTitulo: String?,
    val emissao: String?,
    val entrada: String?,
    val vencimento: String?,
    val tituloAtrasoHabilitado: Boolean,
    val pagamento: String?,
    val valor: String?,
    val aAprovar: String?,
    val saldo: String?,
    val observacao: String?,
    val status: String?,
)
