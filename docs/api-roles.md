# API de cargos — SCRUM-481

Os endpoints seguem a arquitetura do módulo `role`: controller, DTOs, `RoleService`
e repository. O serviço existente de importação de cargos foi preservado. As rotinas
PostgreSQL são declaradas como métodos nativos `@Query` no `RoleRepository` JPA,
seguindo o padrão do `AuthRepository`. Projections mapeiam as colunas retornadas.
O `RoleManagementRepository` converte as projections em DTOs e traduz SQLSTATEs.
O repositório JPA também mantém as importações e consultas já existentes.

As rotas usam nomes em inglês, como os demais módulos da API: `/roles` para
cargos e `/nrs/options` para as opções. Os campos JSON continuam seguindo os
payloads da especificação. As mensagens das constraints das requests ficam em
`src/main/resources/ValidationMessages.properties`.

Todas as chamadas exigem `Authorization: Bearer <token Firebase>` e perfil `GESTOR`
ou `GESTOR_WORKSPACE`. O `firebase_uid` enviado deve corresponder ao token. O banco
valida gestor ativo e workspace. As respostas usam `ApiResult`, como os demais módulos;
os arrays, o cargo editado e o booleano de status ficam na propriedade `data`.

| Task | Método e rota | Rotina / fonte |
| --- | --- | --- |
| SCRUM-482 | `GET /roles?firebase_uid=...` | `fn_seleciona_cargos(TEXT)` |
| SCRUM-483 | `GET /roles/{roleId}/nrs?firebase_uid=...` | `fn_seleciona_cargo_nrs(TEXT, BIGINT)` |
| SCRUM-484 | `GET /roles/{roleId}/nrs/options?firebase_uid=...` | Mongo + `fn_seleciona_cargo_nrs` |
| SCRUM-485 | `POST /roles` | `pr_inseri_cargo(TEXT, TEXT, TEXT)` |
| SCRUM-486 | `PATCH /roles/{roleId}/status` | `fn_altera_status_cargo(TEXT, BIGINT)` |
| SCRUM-487 | `PUT /roles/{roleId}` | `fn_altera_cargo(TEXT, BIGINT, TEXT, TEXT)` |
| SCRUM-488 | `POST /roles/{roleId}/nrs` | `pr_alterar_cargo_nrs(TEXT, BIGINT, JSONB)` |

## Payloads

Criação e edição recebem:

```json
{"firebase_uid":"uid-do-gestor","nome":"Gerente","status":"Ativo"}
```

O nome, após remover espaços nas extremidades, deve ter entre 2 e 255 caracteres.
O status aceita `Ativo` ou `Inativo`, sem distinguir maiúsculas e minúsculas.
A criação responde 201, sem objeto em `data`, pois a procedure não retorna um ID.
A edição responde 200 com os campos de `vw_cargos`, mapeando `role` para `nome`.

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

Antes de chamar a procedure, a API consulta os códigos no catálogo relacional. Uma
NR marcada que ainda não está nesse catálogo retorna 400 com os códigos
indisponíveis e orientação para atualizar a lista, sem gravar parte do lote.
Itens desmarcados que não existem mais no catálogo são ignorados, permitindo
salvar uma lista de opções carregada antes de uma mudança de catálogo. Itens
desmarcados existentes continuam sendo enviados para efetivar a desvinculação.
Mesmo se a lista resultante ficar vazia, a procedure é chamada e mantém as
validações de identidade, cargo e workspace.

## Banco e configuração

Assinaturas e colunas conferidas nos arquivos `database/functions/10_...` a `13_...`,
`database/procedures/03_...` e `04_...`, e `database/views/03_vw_cargos.sql`
do repositório `banco-de-dados`. Essas rotinas e a view precisam estar instaladas
no PostgreSQL conectado à aplicação; a API não as cria automaticamente.

As funções de consulta retornam listas vazias para gestor/cargo sem acesso,
conforme o SQL. As opções novas combinam documentos com `usabilidade: "Colaborador"`
do Mongo com os códigos realmente existentes em `nr_catalogo` no PostgreSQL.
O `_id` numérico é comparado com `codigo_nr`, e a descrição continua vindo do Mongo.
Um documento ainda não sincronizado com o catálogo relacional não é oferecido
como nova opção. NRs já vinculadas ao cargo permanecem nas opções mesmo quando
o documento deixa de ser oferecido pelo Mongo, usando a descrição da consulta SQL,
para que o gestor possa desvinculá-las. A lista continua ordenada por código.

Essa leitura não insere NRs nem altera a sincronização de catálogos. A publicação
e sincronização de NRs mantém o fluxo próprio de revisão. A API não depende de
um ajuste no frontend para evitar o envio de referências inexistentes ao banco.

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
parciais de NRs e duplicidades. Testes do repository verificam o mapeamento das projections JPA,
os parâmetros JSONB e a tradução de SQLSTATE. Esses testes usam mocks e não substituem
uma validação das rotinas instaladas em um ambiente PostgreSQL/MongoDB.
