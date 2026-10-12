# API de unidades — SCRUM-507

Os cinco endpoints usam `Authorization: Bearer <token Firebase>` e os perfis
`GESTOR` e `GESTOR_WORKSPACE`. O UID vem exclusivamente de `AuthenticatedUser`,
preenchido pelo filtro Firebase existente. Não envie `firebase_uid` no body ou na URL.
O banco continua responsável por verificar o gestor ativo e seu workspace.

O `UnitRepository` concentra as consultas e chamadas nativas JPA, retornando projections
e valores do banco. O `UnitService` coordena as operações, valida as entradas e traduz
os SQLSTATEs conhecidos para as exceções da API. O `UnitResponseMapper` converte as
projections e documentos MongoDB em DTOs. A criação de entidades do fluxo de workspace
continua usando o `UnitMapper` existente.

As rotas estão em inglês, conforme a convenção solicitada. Os campos JSON exigidos
pelo contrato e as colunas PostgreSQL estão mapeados explicitamente para identificadores
Java em inglês. As respostas seguem `ApiResult`, com o resultado em `data`.

| Subtarefa | Método e rota | Fonte |
| --- | --- | --- |
| SCRUM-508 | `GET /units` | `fn_seleciona_unidades(firebase_uid)` |
| SCRUM-509 | `PATCH /units/{unitId}/status` | `fn_altera_status_unidade(firebase_uid, unitId)` |
| SCRUM-510 | `GET /units/{unitId}/nrs/associated` | `fn_seleciona_unidade_nrs(firebase_uid, unitId)` |
| SCRUM-511 | `GET /units/{unitId}/nrs` | MongoDB `NRs` + `fn_seleciona_unidade_nrs` |
| SCRUM-512 | `POST /units/{unitId}/nrs` | `pr_alterar_unidade_nrs(firebase_uid, unitId, JSONB)` |

A listagem retorna `id_unidade`, `id_workspace`, `nome`, `status`,
`quantidade_colaboradores`, `cep`, `rua`, `cidade`, `bairro`, `estado` e `complemento`,
conforme `vw_unidades`. O endereço pode ser nulo. O PATCH não exige body e retorna
`data: true` para ativa e `data: false` para inativa. Os triggers PostgreSQL executam
as regras de desativação dos usuários vinculados.

As NRs associadas retornam `nr` e `descricao`, vindos de `codigo_nr` e `descricao`
da função. As opções usam o modelo `NrDocument` existente, consultando explicitamente
a collection `NRs` (nome sensível a maiúsculas) com `usabilidade: "Empresa"`.
Usam `_id` numérico e `descricao` do MongoDB e acrescentam `ativo` de acordo com
as associações PostgreSQL. Essa consulta não depende do nome configurado para a
collection dos outros módulos. As opções são ordenadas pelo código.

O POST recebe diretamente um array:

```json
[{"nr":1,"ativo":true},{"nr":17,"ativo":false}]
```

Cada item altera apenas a NR informada; códigos omitidos permanecem associados.
Um array vazio é permitido. `nr` deve ser um inteiro positivo dentro do intervalo
Java/PostgreSQL INTEGER e `ativo` deve ser um booleano JSON. Strings, decimais,
itens nulos, campos ausentes e códigos repetidos são rejeitados. A procedure valida
a existência da NR no catálogo PostgreSQL e altera as associações em uma transação.
O sucesso responde HTTP 200 no envelope existente, sem resultado em `data`.

## Dependência do banco e erros

Assinaturas, campos e SQLSTATEs conferidos na
[PR #31 do banco](https://github.com/Astro-Inter/banco-de-dados/pull/31), referente à
[SCRUM-500](https://luizacursino.atlassian.net/browse/SCRUM-500).
As functions `15` a `17`, a procedure `06` e a view `04` precisam estar instaladas
no PostgreSQL da API. Esta implementação não instala os scripts automaticamente.

As funções de consulta retornam lista vazia para UID/unidade sem acesso e para
unidades sem associações, conforme o contrato SQL. A lista de opções continua
retornando o catálogo elegível com `ativo: false` quando não há associações retornadas.
As mutações convertem `22023` em HTTP 400, `42501` em 403 e `P0002` em 404.
Falhas de conexão, conflitos de integridade e falhas inesperadas mantêm o tratamento
global existente de HTTP 503, 409 e 500. Os detalhes internos do SQL não são expostos.

Não há novos endpoints de criação de unidades nem de alteração de nome/endereço.
O fluxo existente de criação de unidades durante a importação de workspace foi mantido.

## Verificação

Os testes cobrem os cinco contratos HTTP, validações com mensagens em português,
tipos JSON estritos, UID autenticado, autorização por perfil, união dos catálogos,
serialização JSONB, duplicidades e tradução dos SQLSTATEs. As integrações usam mocks;
não substituem a execução contra PostgreSQL e MongoDB com os scripts instalados.
