# Investigação de baixa de título em homologação

Consultas somente de leitura realizadas em 2026-10-21 no endpoint
`https://hmlgruporovema.dealernetworkflow.com.br/aws_gruporovema.aspx`,
operação `TITULO`, modo `CON`, filial Dealer 1.

| Código consultado | Documento/parcela retornado | Status | Valor | Saldo |
| --- | --- | --- | --- | --- |
| 72412 | 58832-1 | PAG | 284,86 | 0,00 |
| 1107684 | 349412-4 | ENB | 279,42 | 279,42 |

A referência solicitada `72412-4` diverge da parcela retornada pelo WS para
`Titulo_Codigo=72412`. O alvo de teste `1107684-4` corresponde à parcela 4.

## Tentativas de obter movimentações

- `Json_dadosfiltro` com código e filial, `Json_titulos=[]`: retorna a seleção
  resumida em `SDT_TituloSelecaoConsultarWS`, sem `SDT_Financeiro_TituloMov`.
- Mesma consulta com `Json_titulos=[{Titulo_Codigo, Titulo_EmpresaCod,
  SDT_Financeiro_TituloMov: []}]`: o WS apenas devolve esse conteúdo de
  `Json_titulos` sem preenchê-lo. Não comprova que a coleção esteja vazia no ERP.

HTTP 200 não implica que todos os parâmetros tenham sido aplicados ou que uma
movimentação tenha sido executada. Conferir erros SOAP, mensagens por registro,
saldo, status e identificação da movimentação após qualquer escrita.

## Dados necessários antes de enviar UPD

O manual menciona a coleção `SDT_Financeiro_TituloMov` no contrato de escrita,
mas não mostra um exemplo de liquidação. A seleção CON não forneceu os códigos
de movimento do título pago, nem o cadastro completo necessário para comprovar
que UPD parcial preserve os demais campos do título.

Precisam ser definidos os códigos financeiros aplicáveis ao recebimento:
tipo CD, tipo de recebimento, conta/agente cobrador de caixa ou banco, tipo de
documento, classificação e ficha razão conforme as exigências do WS. Os códigos
existentes no título (agente cobrador, conta gerencial e tipo de cobrança) não
comprovam quais códigos devem ser utilizados no lançamento do recebimento.

A tentativa de baixa ainda não foi enviada. Não houve alteração de status,
saldo ou lançamento financeiro.

## Reaproveitamento da configuração de adiantamento

Na configuração base de `AUTOVEMA_PVH`, os padrões são:

| Configuração | Padrão | Uso comprovado na automação |
| --- | --- | --- |
| `conta-gerencial-identificador` | `30212001` | Filtra `vFILTRO_CONTAGERENCIAL_IDENTIFICADOR` na seleção de conta por empresa |
| `tipo-codigo` | `5` | Seleciona `vTESOURARIA_TIPOCDCOD` pelo valor da opção |
| `tipo-documento-codigo` | `170` | Seleciona `vTESOURARIA_TIPODOCUMENTOCOD` pelo valor da opção |

O identificador da conta não é obtido do campo interno `ContaGerencialCod`:
a automação pesquisa o identificador e seleciona o resultado. A correspondência
entre `30212001` e o código interno exigido por `TituloMov_ContaGerencialCod`
precisa ser resolvida no cadastro do ERP; não é correto copiá-lo diretamente.

Pelos nomes dos campos, `tipo-codigo` é candidato a `TituloMov_TipoCDCod`,
e `tipo-documento-codigo` a `TituloMov_TipoDocumentoCod`. Essa correspondência
estrutural não comprova que os mesmos tipos usados em adiantamento se aplicam
à liquidação de título. `TituloMov_TipoCDRecebimento` é outro campo e não deve
receber o valor de `tipo-codigo` por suposição.

Não foi localizado um serviço SOAP de consulta ao cadastro de contas/tipos no
WSDL atual. Nenhuma escrita foi realizada nesta verificação.
