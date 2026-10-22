# Contas a receber no Pix Portal

Endpoints implementados:

- `GET /api/contas-receber`: listagem normalizada para o front-pix.
- `POST /api/contas-receber/{tituloId}/pix`: geração/reutilização da cobrança.

O contrato está em `../front-pix/docs/contas-receber-api.md`. As rotas existentes
de automação e adiantamento continuam disponíveis.

## Configuração

Adicionar ao arquivo de configuração do ambiente, sem versionar senhas:

```yaml
dealer:
  web-service:
    enabled: ${DEALER_WS_ENABLED:false}
    endpoint: ${DEALER_WS_ENDPOINT:https://hmlgruporovema.dealernetworkflow.com.br/aws_gruporovema.aspx}
    username: ${DEALER_WS_USERNAME:}
    password: ${DEALER_WS_PASSWORD:}
    max-titulos-por-empresa: 10000

empresa:
  configuracoes:
    AUTOVEMA_PVH:
      # Acrescentar ao cadastro existente, preservando CNPJ, conta Pix etc.
      codigo-dealer: 1
```

O WSDL está em
`https://hmlgruporovema.dealernetworkflow.com.br/aWS_GrupoROVEMA.aspx?wsdl`.
As chamadas usam o endereço `soap:address` informado nele, sem `?wsdl`.
O código `1` de `AUTOVEMA_PVH` foi conferido por consulta em homologação.
Cada outra filial precisa do próprio código, confirmado no respectivo ambiente.
Não reutilizar códigos de homologação em produção sem conferência.

A configuração local ignorada pelo Git em `application-hmg.yml` foi habilitada
com o endpoint de homologação, o mapeamento acima e referências ao primeiro
login de `dealer.credentials`, que funcionou na consulta. `DEALER_WS_USERNAME`
e `DEALER_WS_PASSWORD` permitem trocar por um usuário exclusivo do WS.
As credenciais não são impressas nem incluídas neste documento.

## Consulta e autorização

O backend usa as mesmas autoridades por filial de `/branch`. O `empresaId`
retornado é a chave do cadastro (por exemplo, `AUTOVEMA_PVH`). A consulta aceita
essa chave ou o código Dealer mapeado. Sem cliente, uma filial é obrigatória.
Com `clienteId`, omitir a filial consulta somente as filiais autorizadas,
aplicando o código exato da pessoa em cada chamada SOAP. `ADMIN` não concede
automaticamente acesso a todas as empresas.
Filiais sem mapeamento retornam 503; códigos duplicados também são recusados.
Os resultados SOAP passam por nova verificação de filial e movimento `R`.

O WSDL confirmado usa SOAP 1.1, namespace `DealerNet`, ação
`DealerNetaction/AWS_GRUPOROVEMA.TITULO` e campos `Modo`, `Usuario`, `Senha`,
`Json_dadosfiltro`, `Json_titulos`. O backend envia apenas `Modo=CON`.

A consulta autenticada real revelou os títulos em
`Json_dadosfiltro.SDT_TituloSelecaoConsultarWS`; o parser também aceita a lista
em `Json_titulos` exemplificada no PDF. Os nomes e saldos vêm de
`Titulo_PessoaNom` e `Titulo_Saldo`; `Titulo_NumeroParcela` é `numero-parcela`;
as datas podem incluir `T00:00:00`. O saldo nunca é inferido do valor original.
`Titulo_PessoaCod` foi observado como texto com casas decimais (`"4.00"` para
a Cielo). O mapper normaliza esse código para uma string inteira (`"4"`) e
recusa códigos com parte fracionária diferente de zero.
Caso um campo financeiro obrigatório esteja ausente/inválido, a consulta falha
com 502. O parser XML bloqueia DTDs e entidades externas.

CPF/CNPJ com 11/14 dígitos é enviado como `Pessoa_DocIdentificador`. Esse filtro
foi conferido em homologação. A seleção real não traz o documento do cliente;
nesse caso `clienteDocumento` é nulo. Busca textual por nome, documento do título
ou códigos é aplicada à seleção retornada, sem diferenciar acentos.

`pagina` começa em zero, `tamanho` aceita 20/50/100. Datas são inclusivas.
Ordenação: vencimento, empresaId, tituloId. O WS não documenta paginação;
o backend filtra a seleção e, com `todos=true`, devolve todos os resultados para
paginação local no frontend. Sem esse parâmetro, mantém `pagina`/`tamanho` para
outros consumidores.

O seletor de clientes usa `/api/contas-receber/clientes?busca=CPF_OU_CNPJ`.
A busca exige documento completo (11/14 dígitos, com ou sem pontuação) e chama
`CONSULTAPESSOA` com `Pessoa_documento`, sem consultar títulos ou enviar datas.
Nome, código ou documento incompleto retornam 400 antes de chamar o WS.
A filial opcional é validada pelas permissões; sem filial, a primeira permitida
é usada como contexto de autorização. Sem acesso a filiais, retorna 403.
A operação SOAP de pessoas não recebe filial: o documento é consultado globalmente.
`Xml_pessoa` contém XML escapado com `SDT_FSPessoaOutCollection/SDT_FSPessoaOut`;
`Pessoa_Codigo`, `Pessoa_Nome` e `Pessoa_DocIdentificador` formam a seleção.
Código é normalizado para inteiro exato e documento é conferido com o consultado.
`Pessoa não encontrada` retorna lista vazia. SOAP Fault, mensagem de recusa e XML
inválido retornam erro, sem fallback para varredura de títulos.

O filtro opcional `clienteId` envia `Titulo_PessoaCod` ao WS e confere o código
exato no retorno. Sem cliente, as duas datas são obrigatórias e o máximo é de
30 dias inclusivos. Com cliente selecionado, as datas são opcionais e qualquer
intervalo válido é aceito, mantendo a restrição ao código da pessoa. Se datas
forem omitidas, a chamada SOAP usa 1900-01-01/9999-12-31 nas extremidades ausentes.
A UI inicia em hoje e termina em hoje + 29 dias. Datas ausentes/invertidas,
cliente inválido e, sem cliente, ausência de filial ou período acima de 30 dias
retornam 400 antes de consultar o WS.
Acima do limite por filial, a consulta falha com 502 e
pede um período menor, sem retornar um total truncado. Em filiais volumosas,
usar filtros de vencimento para reduzir custo/tempo. Os timeouts HTTP seguem
a configuração Spring já existente em `spring.http.client`.

## Cobrança

O body contém apenas `empresaId`. O backend revalida o acesso e reconsulta o
título por código/filial. A conta de recebimento vem do cadastro da filial, e
o valor vem do saldo aberto atual, com `BigDecimal`.

A consulta ao histórico combina filial, código de título e `CONTAS_RECEBER`.
Pix criado e não expirado, com o mesmo valor, é reutilizado. Pix vigente com
outro valor ou pagamento já confirmado retorna 409, para conferir conciliação.
Títulos pagos/cancelados/sem saldo também são recusados. A nova transação é
persistida com filial, usuário, cliente, saldo, QR Code, vencimento, expiração
e próxima verificação. A resposta inclui `documento`, usado pelo frontend.

Emissões concorrentes do mesmo título são serializadas nesta instância. Para
executar várias réplicas emitindo simultaneamente, será necessário coordenar
essa emissão entre instâncias e reconciliar respostas incertas do PSP antes
de novas tentativas. Falha após a emissão no PSP e antes da persistência não
tem recuperação automática nesta implementação.

O monitoramento existente consulta o pagamento. A baixa de `CONTAS_RECEBER`
continua sem handler: se houver pagamento, o fluxo registra `ERRO_BAIXA` e
mantém a informação de pagamento confirmado. Esta implementação não usa
`UPD`, não altera `Titulo_Status` nem presume liquidação pelo SOAP. A baixa
automática depende de validar o lançamento financeiro com o fornecedor.

## Diagnóstico de falhas do WS

Mensagens de comunicação são produzidas pelo dealerlenium, não pelo WS.
Timeout de conexão/resposta retorna 504; falhas de DNS, conexão, TLS ou resposta
incompleta retornam 502 com o tipo e a mensagem técnica da causa. O log mantém
os frames e a cadeia de causas com mensagens sanitizadas. Senha/usuário
configurados e suas formas escapadas são ocultados.

Se houve resposta HTTP com erro, a mensagem informa o status recebido do WS.
Quando presente, inclui `faultcode`/`faultstring` do SOAP Fault ou `Outmensagem`.
SOAP Fault em HTTP 200 e mensagens de erro por título também exibem o motivo
fornecido pelo WS. Não é registrado o envelope de autenticação nem o corpo
completo da resposta. As descrições são limitadas a 500 caracteres.

Logs antigos com apenas "Falha de comunicacao com o Web Service de titulos."
não permitem recuperar a causa descartada: repetir a consulta com esta versão
é necessário para obter o diagnóstico específico.

## Validação

Foi feita uma consulta autenticada `CON` em homologação, limitada a um dia e
uma filial, e outra com CPF fictício para conferir o filtro. Não houve emissão
real de Pix nem alteração de títulos durante a validação.

```sh
./gradlew test --tests '*DealerTitulo*Test' --tests '*ContasReceber*Test' --tests '*ContaReceberPixServiceTest' --tests '*EmpresaPropertiesBindingTest' --tests '*OpenTransactionSettlementProcessorTest'
./gradlew bootJar
```

Os testes automatizados usam respostas sintéticas com o formato observado no
WS. Cobrem contrato HTTP, filtros, autorização, saldo, reutilização de Pix,
falhas SOAP, autenticação, limites e bloqueio de entidades externas.


## Verificação de CONSULTAPESSOA em homologação (2026-10-21)

Consulta somente de leitura com CPF conhecido retornou uma pessoa em aproximadamente
0,7 segundo, com `Pessoa_Codigo`, `Pessoa_Nome`, `Pessoa_TipoPessoa`,
`Pessoa_DocIdentificador`, `Pessoa_RG_InscricaoEstadual`, `Pessoa_OrgaoEmissor`,
`Pessoa_InscricaoSuframa`, `Pessoa_InscricaoMunicipal`, `Pessoa_Sexo`,
`Pessoa_Email`, `Pessoa_Nacionalidade`, `Pessoa_EstadoCivil`, `Pessoa_Nascimento`,
`Pessoa_Mensagem` e coleções `Endereco`, `Telefone`, `MeioContato`.
Dados pessoais usados na verificação não foram incorporados ao repositório.

| Variação enviada | Resultado observado |
| --- | --- |
| CPF completo sem pontuação em `Pessoa_documento` | Uma pessoa encontrada |
| Mesmo CPF com pontuação em `Pessoa_documento` | Pessoa não encontrada |
| Código real da pessoa em `Pessoa_documento` | Pessoa não encontrada |
| Nome completo real em `Pessoa_documento` | Pessoa não encontrada |
| Substituir o campo por `Pessoa_Codigo`, `Pessoa_pessoa_codigo`, `Pessoa_Nome` ou `Pessoa_DocIdentificador` | SOAP Fault `SOAP-ENV:Client` |
| Documento inválido junto com código ou nome correto em campo adicional | Mesmo SOAP Fault |
| CPF correto junto com código incorreto em campo adicional | Mesmo SOAP Fault |

Nas variações rejeitadas, o servidor respondeu HTTP 200 com Fault aninhado em
`WS_GrupoROVEMA.CONSULTAPESSOAResponse`, `faultstring` igual a
`Error reading ns1:WS_GrupoROVEMA.CONSULTAPESSOA` e `detail` igual a `2`.
O contrato observado corresponde ao WSDL (`Pessoa_documento`), enquanto a tabela
do PDF menciona `Pessoa_pessoa_codigo`, que não foi aceito nos testes.
A aplicação remove pontuação antes de chamar o WS. Não há suporte confirmado a
busca de pessoas por nome ou código nessa operação.

## Filtros exatos de identificação do título

`GET /api/contas-receber` aceita `lancamento`, `documento` e `parcela`, opcionais,
inteiros positivos. São combinados com os filtros existentes. Mapeamento SOAP:
`lancamento` → `Titulo_Codigo`, `documento` → `Titulo_Numero`, `parcela` →
`Titulo_Parcela`. O resultado é novamente conferido por esses valores, mesmo
quando o WS ignora algum filtro. Não dispensam filial e datas sem `clienteId`.
