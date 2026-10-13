# Gestores — SCRUM-525

Política confirmada pelo solicitante: somente `GESTOR_WORKSPACE` ativo administra
gestores de todas as unidades do próprio workspace. A identidade vem do token
Firebase validado; perfil, status e workspace são conferidos no banco. Os alvos
são exclusivamente `GESTOR`, protegendo a própria conta `GESTOR_WORKSPACE`.

## GET /managers — SCRUM-526

Retorna HTTP 200 com `ApiResult` e uma lista de `id_usuario`, `nome`, `email`,
`unidade_id`, `unidade` e `status`. Consulta `vw_gestores` pelo workspace autorizado,
ordenada por nome e ID. Inclui PRE_CADASTRADO, ATIVO e DESATIVADO, com vínculos
inativos e vários gestores por unidade. Não há paginação, filtros ou CPF na lista.
Uma lista sem registros retorna `[]`. Senha e Firebase UID nunca são expostos.

## GET /managers/{id} — SCRUM-527

Retorna HTTP 200 com `ApiResult` e `id_usuario`, `nome`, `cpf`, `email`,
`modalidade`, `status`, `cargo_id`, `cargo`, `unidade_id`, `unidade` e `workspace_id`.
Modalidade pode ser nula em registros legados. ID inválido retorna 400;
inexistente, outro perfil ou outro workspace retornam o mesmo 404.

## POST /managers — SCRUM-528

Request: `{"collaboratorId":123,"status":"ATIVO"}`. Ambos são obrigatórios;
status aceita somente ATIVO ou DESATIVADO. Retorna 201 com detalhes e Location
`/managers/{id}`. Promove a conta COLABORADOR já ativa e com Firebase UID, mantendo
todos os campos e vínculos. Cargo e unidade devem estar ativos no mesmo workspace.
Não cria conta Firebase nem envia convite. Bloqueia a linha do usuário dentro da
transação e revalida elegibilidade; promoção repetida ou outro perfil retorna 409.
Registro fora do workspace retorna 404, candidato inelegível retorna 400.
A autenticação existente consulta perfil e status em cada requisição protegida:
o token previamente emitido passa a refletir GESTOR ou ter acesso bloqueado quando
DESATIVADO. `User.role` mapeia explicitamente a coluna existente `cargo_id`.

## PUT /managers/{id} — SCRUM-529

Todos os campos são obrigatórios e `null` é inválido: `nome`, `cpf`, `email`,
`roleId`, `unitId`, `modalidade`, `status`. Modalidade: HIBRIDO, REMOTO ou PRESENCIAL.
Retorna 200 com os detalhes. Nome é aparado, CPF validado e armazenado com onze
dígitos, e-mail aparado e convertido para minúsculas como na importação existente.
Conflitos de CPF/e-mail retornam 409. ID, tipo, criado_em e Firebase UID são preservados.
Vínculos novos precisam estar ativos no próprio workspace; vínculos inativos
existentes podem ser mantidos. A unidade única é substituída por `unitId`.
PRE_CADASTRADO pode ser mantido para editar um registro legado, mas não é destino
de uma transição nem pode ser ativado por este fluxo. Ativação exige Firebase UID
e vínculos ativos; solicitação do status atual mantém o estado.

E-mail de conta Firebase é atualizado na identidade existente, invalidando sua
verificação. A transação PostgreSQL é concluída dentro de TransactionTemplate.
Falha de atualização ou commit aciona restauração do e-mail e da verificação.
Se a restauração falhar, a API desativa a conta em transação independente e registra
a necessidade de reconciliação operacional. Se o próprio banco estiver indisponível
nessa contenção, a falha também é registrada. Identidade previamente divergente
retorna 409 e exige reconciliação antes de editar. Não há senha neste contrato.

## Dependências

A view pertence à SCRUM-524, PR Astro-Inter/banco-de-dados#33. A API não cria nem
altera objetos SQL de produção. No banco configurado, a view foi confirmada com
as onze colunas do contrato, em conexão somente leitura e consulta `WHERE false`.
Os testes PostgreSQL usam uma cópia do contrato existente em schema temporário
exclusivo de um banco local descartável; não acessam contas reais.
GET /collaborators não está presente na main utilizada nem no Swagger publicado
consultado nesta entrega. O dropdown depende da entrega de colaboradores
(SCRUM-517). GET /roles e GET /units são reutilizados para a edição.
