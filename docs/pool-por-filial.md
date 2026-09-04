# Pool de sessoes por filial

Cada credencial de `dealer.credentials` vira uma sessao independente do Dealernet
(Chrome + login proprio + `ASP.NET_SessionId` proprio). O campo `empresa` de cada
credencial define **quais filiais aquele login atende**:

| `empresa` | efeito |
|---|---|
| id da filial (ex.: `AUTOVEMA_PVH`) | **pool dedicado**: a sessao faz o `changeFilial` logo apos o login e fica travada nessa filial; so ela atende transacoes dessa filial |
| `all` | atende qualquer filial |
| vazio / ausente | **pool geral**: atende todas as filiais, exceto as que ja tem login dedicado |

```yaml
dealer:
  credentials:
    - username: ${DEALER_USERNAME:SUPORTE}
      password: ${DEALER_PASSWORD:...}
      empresa: ""              # geral
    - username: ${DEALER_USERNAME_2:RODRIGO CESAR}
      password: ${DEALER_PASSWORD_2:...}
      empresa: AUTOVEMA_PVH    # dedicado
```

O id precisa existir em `empresa.configuracoes` (`empresa.yml`); a aplicacao falha no
startup se nao existir, e loga um `warn` para toda filial configurada que nenhuma
credencial consiga atender.

## Duas formas de transacao

1. **Com lock de filial** — o chamador informa a filial (`branchId` do
   `POST /pix/adiantamento`, `transaction.empresa` das baixas e do monitoramento,
   `branchId` opcional em `GET /api/debug/pessoa/{id}`). O pool empresta a sessao
   dedicada aquela filial; como ela ja esta posicionada desde o login, **nada e feito**
   na troca de filial. Se a sessao dedicada estiver ocupada, a requisicao **espera** ela
   liberar - uma filial dedicada nunca cai no pool geral.
2. **Geral** — o chamador nao sabe a filial (`GET /pix/contas-receber/{id}` e
   `GET /api/titulos/valor` buscam o titulo no grid com filtro `all`). A requisicao usa
   os logins gerais / `all`. Passar `all` como filial equivale a nao informar filial.

### Blindagem da sessao dedicada

Cada requisicao roda numa **aba nova** (`DealerSession.runInTab`), inclusive na sessao
dedicada - a aba master fica so com o login/travamento de filial e com o job que
mantem o slot de licenca vivo. A aba nova abre ja na filial travada porque a filial e
estado de sessao do servidor, nao da aba.

Para essa premissa nao falhar em silencio, a sessao dedicada **confere o cabecalho na
primeira aba depois de cada login** (`conferirFilialTravada`): uma leitura de DOM, sem
navegar o menu. Se a filial bater, nao confere mais nessa sessao de login; se divergir,
loga `error` com a filial esperada e a encontrada, corrige com `changeFilial` antes da
transacao e **passa a conferir em toda aba** ate o proximo login.

Quando a filial e informada e a sessao emprestada **nao** e a dedicada (login geral ou
`all`), o proprio pool executa o `changeFilial` antes da acao - o `AuthenticatedPage.changeFilial`
ja verifica o cabecalho (`AuthenticatedPage.XPATH_FILIAL`) e nao navega o menu se a sessao ja estiver na
filial certa.

## Licencas

Os logins dedicados existem por limitacao de licencas do Dealernet: a sessao e criada uma
vez, mantida viva pelo monitor de expiracao (`MainTabSessionExpiryJob`, a cada 10s nas
sessoes ociosas) e so refaz login quando o Dealernet derruba a sessao. Nao ha
login/logout por requisicao - o slot da licenca fica reservado.

## Onde mexer

- `DealerProperties.DealerCredential.empresa` - o parametro.
- `service/DealerSessionScope.kt` - `DealerSessionScope` (Dedicada/Todas/Geral) e
  `DealerSessionRouting` (quem aceita o que, ordem de preferencia, filial sem cobertura).
- `service/BrowserSessionManager.kt` - emprestimo com afinidade (lock + condition) e
  `garantirFilial`.
- `service/DealerSession.kt` - `travarFilialSeNecessario()`, chamado apos o login.
