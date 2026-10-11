# API de cargos — SCRUM-481

Os endpoints seguem a arquitetura do módulo `cargo`: controller, DTOs, `CargoService`
e repository. O serviço existente de importação de cargos foi preservado. As rotinas
PostgreSQL são chamadas pelo `CargoManagementRepository` via JDBC; o `CargoRepository`
JPA continua atendendo importações e consultas já existentes.

Todas as chamadas exigem `Authorization: Bearer <token Firebase>` e perfil `GESTOR`
ou `GESTOR_WORKSPACE`. O `firebase_uid` enviado deve corresponder ao token. O banco
valida gestor ativo e workspace. As respostas usam `ApiResult`, como os demais módulos;
os arrays, o cargo editado e o booleano de status ficam na propriedade `data`.

| Task | Método e rota | Rotina / fonte |
| --- | --- | --- |
| SCRUM-482 | `GET /cargos?firebase_uid=...` | `fn_seleciona_cargos(TEXT)` |
| SCRUM-483 | `GET /cargos/{id_cargo}/nrs?firebase_uid=...` | `fn_seleciona_cargo_nrs(TEXT, BIGINT)` |
| SCRUM-484 | `GET /cargos/{id_cargo}/nrs/opcoes?firebase_uid=...` | Mongo + `fn_seleciona_cargo_nrs` |
| SCRUM-485 | `POST /cargos` | `pr_inseri_cargo(TEXT, TEXT, TEXT)` |
| SCRUM-486 | `PATCH /cargos/{id_cargo}/status` | `fn_altera_status_cargo(TEXT, BIGINT)` |
| SCRUM-487 | `PUT /cargos/{id_cargo}` | `fn_altera_cargo(TEXT, BIGINT, TEXT, TEXT)` |
| SCRUM-488 | `POST /cargos/{id_cargo}/nrs` | `pr_alterar_cargo_nrs(TEXT, BIGINT, JSONB)` |

## Payloads

Criação e edição recebem:

```json
{"firebase_uid":"uid-do-gestor","nome":"Gerente","status":"Ativo"}
```

O nome, após remover espaços nas extremidades, deve ter entre 2 e 255 caracteres.
O status aceita `Ativo` ou `Inativo`, sem distinguir maiúsculas e minúsculas.
A criação responde 201, sem objeto em `data`, pois a procedure não retorna um ID.
A edição responde 200 com os campos de `vw_cargos`, mapeando `cargo` para `nome`.

A alternância recebe `{"firebase_uid":"uid-do-gestor"}` e responde 200 com
`data: true` quando ficou ativo ou `data: false` quando ficou inativo.
A desativação executa os triggers existentes do banco, inclusive para usuários vinculados.

A atualização de NRs recebe:

```json
{"firebase_uid":"uid-do-gestor","nrs":[{"nr":1,"ativo":true},{"nr":17,"ativo":false}]}
```

Cada item altera apenas a NR informada; NRs omitidas permanecem vinculadas.
Uma lista vazia é permitida. Códigos devem ser inteiros positivos, sem repetição,
e cada item exige `ativo` booleano. A procedure valida a existência dos códigos
em `nr_catalogo` e executa a alteração em uma transação.

## Banco e configuração

Assinaturas e colunas conferidas nos arquivos `database/functions/10_...` a `13_...`,
`database/procedures/03_...` e `04_...`, e `database/views/03_vw_cargos.sql`
do repositório `banco-de-dados`. Essas rotinas e a view precisam estar instaladas
no PostgreSQL conectado à aplicação; a API não as cria automaticamente.

As funções de consulta retornam listas vazias para gestor/cargo sem acesso,
conforme o SQL. Na lista de opções, apenas documentos com `usabilidade: "Colaborador"`
são retornados, usando `_id` numérico, `descricao` do Mongo e comparação com `codigo_nr`.

O modelo existente usa a collection `nrs`. A documentação do banco propõe
`nr_catalogo` e a especificação também menciona `NRs`; o nome real não foi confirmado
em uma conexão ao Mongo. Para usar outro nome, configure `astro.mongodb.nr-collection`
(variável de ambiente `ASTRO_MONGODB_NR_COLLECTION`), por exemplo `NRs`.

SQLSTATE `22023` responde 400; `42501`, 403; `P0002`, 404.
Conflitos de integridade e indisponibilidade continuam usando os handlers existentes
para 409 e 503. Falhas inesperadas permanecem 500.

## Validação

Testes de controller verificam rotas, payloads, campos JSON e respostas HTTP.
Testes de serviço cobrem identidade, limites de nome, união das opções, alterações
parciais de NRs e duplicidades. Testes do repository verificam o mapeamento da view,
os parâmetros JSONB e a tradução de SQLSTATE. Esses testes usam mocks e não substituem
uma validação das rotinas instaladas em um ambiente PostgreSQL/MongoDB.
