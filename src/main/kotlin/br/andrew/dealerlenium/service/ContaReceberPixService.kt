package br.andrew.dealerlenium.service

import br.andrew.dealerlenium.infrastructure.configurations.EmpresaProperties
import br.andrew.dealerlenium.model.PixPagamentoResponse
import br.andrew.dealerlenium.model.TipoTransacao
import br.andrew.dealerlenium.model.TransactionDocument
import br.andrew.dealerlenium.model.TransactionStatus
import br.andrew.dealerlenium.repositorys.TransactionRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class ContaReceberPixService(
    private val contas: ContasReceberService,
    private val uzziPix: UzziPixService,
    private val repository: TransactionRepository,
    private val empresas: EmpresaProperties,
    private val monitoring: TransactionMonitoringPolicy,
    private val currentUser: CurrentUserService,
    private val clock: Clock,
) {
    // Stripes fixas serializam emissões concorrentes do mesmo título nesta instância.
    private val locks = Array(128) { ReentrantLock() }

    fun gerar(tituloId: Int, empresaId: String, authentication: Authentication?): PixPagamentoResponse {
        require(tituloId > 0) { "Codigo do titulo deve ser positivo." }
        val empresa = contas.empresaAutorizada(empresaId, authentication)
        val lockKey = "${empresa.id}:$tituloId"
        return locks[Math.floorMod(lockKey.hashCode(), locks.size)].withLock {
            val titulo = contas.consultarTitulo(empresa, tituloId)
            if (!titulo.podeGerarPix || titulo.saldoAberto.signum() <= 0) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Titulo nao permite cobranca Pix ou nao possui saldo aberto.")
            }
            val agora = clock.instant()
            val historico = repository.findAllByIdLancamentoAndEmpresaAndTipoTransacaoOrderByCreatedAtDesc(
                tituloId, empresa.id, TipoTransacao.CONTAS_RECEBER,
            )
            if (historico.any { it.status in setOf(TransactionStatus.PAGO, TransactionStatus.ERRO_BAIXA) || it.pagamentoConfirmadoEm != null }) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Titulo possui Pix pago. Confira a conciliacao no ERP antes de gerar outra cobranca.")
            }
            val ativas = historico.filter { it.status == TransactionStatus.CRIADO && it.pixExpiraEm.isAfter(agora) }
            if (ativas.any { it.valor.compareTo(titulo.saldoAberto) != 0 }) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Saldo do titulo mudou e existe Pix vigente com outro valor. Aguarde a expiracao ou concilie a cobranca anterior.")
            }
            ativas.firstOrNull()?.let { return@withLock it.toPixPagamentoResponse().also { response -> response.documento = it } }

            val retorno = uzziPix.createPixByEmpresa(
                empresa.id, "PIX-${UUID.randomUUID()}", titulo.saldoAberto,
            )
            val geradoEm = retorno.data.generatedAt?.toInstant() ?: agora
            // RequestPix utiliza expiration=3600 segundos quando o PSP omite expiresAt.
            val expiraEm = retorno.data.expiresAt?.toInstant() ?: geradoEm.plus(1, ChronoUnit.HOURS)
            val response = PixPagamentoResponse(
                idLancamento = tituloId,
                tipoTransacao = TipoTransacao.CONTAS_RECEBER,
                status = titulo.situacao.name,
                valor = titulo.saldoAberto,
                descricao = "Titulo ${titulo.documento} / ${titulo.parcela ?: "1"} - ${titulo.empresaNome}",
                nomeRecebedor = titulo.clienteNome,
                expiracaoEm = expiraEm.toString(),
                retornoPix = retorno,
            )
            val configuracao = empresas.getEmpresaOrThrow(empresa.id)
            val transaction = TransactionDocument(
                idLancamento = tituloId,
                tipoTransacao = TipoTransacao.CONTAS_RECEBER,
                txId = response.txId,
                reference = retorno.data.reference,
                empresa = empresa.id,
                empresaCod = empresa.codigoDealer.toString(),
                clienteNome = titulo.clienteNome,
                clienteCodigo = titulo.clienteId,
                valor = titulo.saldoAberto,
                statusTituloRef = titulo.situacao.name,
                descricao = response.descricao,
                numeroParcela = titulo.parcela,
                vencimentoTitulo = titulo.vencimento.toString(),
                pixGeradoEm = geradoEm,
                pixExpiraEm = expiraEm,
                qrCode = response.qrCode,
                qrCodeBase64 = response.qrCodeBase64,
                cnpjContaRecebimento = configuracao.cnpj,
                chavePixRecebimento = configuracao.chavePix,
                nomeTitularContaRecebimento = configuracao.titular,
                usuarioGeracao = currentUser.getCurrentUsernameOrNull(),
                createdAt = agora,
                proximaVerificacaoEm = monitoring.proximaExecucao(TipoTransacao.CONTAS_RECEBER, geradoEm),
            )
            response.documento = repository.save(transaction)
            response
        }
    }
}
