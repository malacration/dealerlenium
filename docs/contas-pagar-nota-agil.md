# Contas a pagar para o Nota Agil

O dealerlenium concentra as integracoes com o Dealernet Workflow. O Nota Agil importa notas de
entrada (contas a pagar) do Dealer por aqui, sem falar direto com o WS.

## Endpoints (somente leitura)

Exigem token Keycloak (realm `rovema`) de service account com a role `integracao_contas_pagar`.
A authority so e concedida quando:
- o `azp` do token esta em `keycloak.integracao-client-ids` (padrao `nota-agil-integracao`) e o token e
  da service account desse client (`preferred_username = service-account-<azp>`; `client_id`/`clientId`,
  quando vier, igual ao azp); token de usuario (mesmo logado pelo client permitido) ou de outro client
  nao recebe a authority, mesmo com a role;
- a role vem do realm ou de um client em `keycloak.integracao-role-clients` (padrao `sapvema`, onde a
  role do sap-rovema esta); role de mesmo nome em outro client e ignorada.
A mesma service account serve ao sap-rovema e ao dealerlenium.

- `GET /api/contas-pagar/empresas`: empresas de `empresa.configuracoes` com `codigo-dealer` e CNPJ
  valido: `[{codigoDealer, cnpj, nome}]`.
- `POST /api/contas-pagar/notas-entrada/listagem`: corpo
  `{empresaCodigo, dataInicial, dataFinal, cpfCnpjFornecedor?, numeroNota?}` (periodo de ate 31 dias,
  datas AAAA-MM-DD) ou `{empresaCodigo, ids: [...]}` (ate 100 ids devolvidos pela listagem).

## Regra

Metodo `TITULO` do WS em modo `CON`, `Titulo_MovimentoFinanceiro = P`, `TituloTipoData = ENT`.
Titulos da mesma nota sao agrupados: id `nf{Titulo_NotaFiscalCod}`, ou `p{Titulo_PessoaCod}-n{numero}`
sem a nota vinculada, ou `t{Titulo_Codigo}` (titulo sem nota fiscal; volta com `problema`).
Cancelados, de outra empresa ou a receber sao descartados mesmo se o WS ignorar o filtro.
Codigo em `service/NotasEntradaContasPagarService.kt`.

## Rodar so para estas consultas

`DEALER_STARTUP_LOGIN_ENABLED=false`, `DEALER_TRANSACTION_MONITORING_ENABLED=false`: sem login no
navegador nem monitoramento de PIX; as consultas usam so o WS (`dealer.web-service.*`).
