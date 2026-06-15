# API de Debug

Documentacao dos endpoints de debug disponiveis para o frontend.

## Base URL

Ambiente local:

```text
http://localhost:8080
```

Todos os endpoints descritos abaixo exigem:

```http
Authorization: Bearer <token JWT>
```

O usuario autenticado precisa ter o papel `debug` no Keycloak. O backend aceita
o papel tanto em `realm_access.roles` quanto em qualquer entrada de
`resource_access`. A comparacao nao diferencia maiusculas de minusculas.

Sem token valido, a API retorna `401 Unauthorized`. Com token valido, mas sem o
papel `debug`, retorna `403 Forbidden`.

## Erros

Erros JSON seguem este formato:

```ts
export interface ApiError {
  status: number;
  error: string;
  message: string;
  path: string;
  timestamp: string; // ISO 8601
}
```

Exemplo:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Cliente 999999 nao encontrado",
  "path": "/api/debug/pessoa/999999",
  "timestamp": "2026-06-15T10:30:00.000-04:00"
}
```

## Consultar pessoa

Busca os dados de uma pessoa no Dealer.

```http
GET /api/debug/pessoa/{id}
```

### Parametros

| Nome | Local | Tipo | Obrigatorio | Regra |
|---|---|---:|---:|---|
| `id` | path | integer | sim | maior que zero |

### Resposta `200 OK`

```ts
export interface Cliente {
  codigo: number;
  nome: string | null;
  nomeFantasia: string | null;
  cpfCnpj: string | null;
  municipio: string | null;
  uf: string | null;
  ativo: boolean;
}
```

Exemplo:

```json
{
  "codigo": 123625,
  "nome": "CLIENTE EXEMPLO LTDA",
  "nomeFantasia": "CLIENTE EXEMPLO",
  "cpfCnpj": "00.000.000/0001-00",
  "municipio": "PORTO VELHO",
  "uf": "RO",
  "ativo": true
}
```

### Erros

| Status | Situacao |
|---:|---|
| `400` | `id` nao numerico, menor ou igual a zero, ou cliente nao encontrado |
| `500` | falha interna ou falha de acesso ao Dealer |

Quando a automacao do navegador falha, a mensagem de erro normalmente e:

```text
Falha ao obter acesso ao dealernet
```

### Exemplo frontend

```ts
export async function consultarPessoa(
  apiUrl: string,
  id: number,
  accessToken: string,
): Promise<Cliente> {
  const response = await fetch(
    `${apiUrl}/api/debug/pessoa/${encodeURIComponent(id)}`,
    {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    },
  );

  if (!response.ok) {
    const error: ApiError = await response.json();
    throw new Error(error.message);
  }

  return response.json() as Promise<Cliente>;
}
```

## Listar artefatos

Lista arquivos de diagnostico gerados pela automacao do navegador, ordenados do
mais recente para o mais antigo.

```http
GET /api/debug/artifacts
```

### Resposta `200 OK`

```ts
export interface DebugArtifactsResponse {
  directory: string;
  endpoint: string;
  totalFiles: number;
  files: DebugArtifact[];
}

export interface DebugArtifact {
  name: string;
  extension: string;
  sizeBytes: number;
  sizeLabel: string;
  lastModified: string; // yyyy-MM-dd HH:mm:ss, horario do servidor
  viewUrl: string;
  downloadUrl: string;
}
```

Exemplo:

```json
{
  "directory": "/app/build/reports/dealer-debug",
  "endpoint": "/api/debug/artifacts",
  "totalFiles": 2,
  "files": [
    {
      "name": "20260610-162000-947-cloned-session-failure.txt",
      "extension": "txt",
      "sizeBytes": 1534,
      "sizeLabel": "1.5 KB",
      "lastModified": "2026-06-10 16:20:00",
      "viewUrl": "./files/20260610-162000-947-cloned-session-failure.txt",
      "downloadUrl": "./files/20260610-162000-947-cloned-session-failure.txt?download=true"
    }
  ]
}
```

Se o diretorio ainda nao existir, a API retorna `200 OK` com `totalFiles: 0` e
`files: []`.

### Observacao sobre as URLs retornadas

No contrato atual, `viewUrl` e `downloadUrl` sao relativos e comecam com
`./files/`. Nao use `new URL(file.viewUrl, listingUrl)` porque a URL base nao
termina com `/` e o navegador pode gerar `/api/debug/files/...`.

Monte o caminho usando o nome do arquivo:

```ts
export function artifactUrl(
  apiUrl: string,
  fileName: string,
  download = false,
): string {
  const path =
    `${apiUrl}/api/debug/artifacts/files/${encodeURIComponent(fileName)}`;

  return download ? `${path}?download=true` : path;
}
```

Ao abrir ou baixar o artefato com `fetch`, envie o mesmo header
`Authorization`. Abrir a URL diretamente em uma nova aba nao envia o Bearer
token automaticamente.

```ts
export async function baixarArtefato(
  apiUrl: string,
  fileName: string,
  accessToken: string,
): Promise<Blob> {
  const response = await fetch(artifactUrl(apiUrl, fileName, true), {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (!response.ok) {
    throw new Error(`Falha ao baixar artefato: HTTP ${response.status}`);
  }

  return response.blob();
}
```

## Visualizar ou baixar artefato

```http
GET /api/debug/artifacts/files/{fileName}
GET /api/debug/artifacts/files/{fileName}?download=true
```

### Parametros

| Nome | Local | Tipo | Obrigatorio | Padrao |
|---|---|---:|---:|---|
| `fileName` | path | string | sim | - |
| `download` | query | boolean | nao | `false` |

O `fileName` deve ser codificado com `encodeURIComponent`.

### Resposta `200 OK`

O corpo e o conteudo bruto do arquivo.

| Extensao | `Content-Type` |
|---|---|
| `.html` | `text/html` |
| `.txt`, `.log` | `text/plain` |
| outras | `application/octet-stream` |

Com `download=true`, a resposta inclui `Content-Disposition: attachment`.
Sem esse parametro, o navegador pode exibir arquivos HTML e texto diretamente.

### Erros

| Status | Situacao |
|---:|---|
| `404` | arquivo inexistente ou caminho invalido |

Exemplo:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Arquivo nao encontrado: exemplo.txt",
  "path": "/api/debug/artifacts/files/exemplo.txt",
  "timestamp": "2026-06-15T10:30:00.000-04:00"
}
```

## CORS

O backend aceita credenciais CORS e, por padrao, permite origens locais como:

```text
http://localhost:<qualquer-porta>
http://localhost:4200
http://*.localhost:<qualquer-porta>
```

Outras origens precisam ser adicionadas na configuracao `cors.origins`.

## Seguranca no frontend

Os artefatos podem conter HTML, URLs, texto da pagina e dados exibidos durante a
automacao. Nao injete o conteudo HTML na aplicacao com `innerHTML`. Para
inspecao, prefira abrir o endpoint em uma nova aba ou forcar o download.
