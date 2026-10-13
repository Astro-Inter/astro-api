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

## Dependências

A view pertence à SCRUM-524, PR Astro-Inter/banco-de-dados#33. A API não cria nem
altera objetos SQL de produção. No banco configurado, a view foi confirmada com
as onze colunas do contrato, em conexão somente leitura e consulta `WHERE false`.
Os testes PostgreSQL usam uma cópia do contrato existente em schema temporário
exclusivo de um banco local descartável; não acessam contas reais.
GET /collaborators não está presente na main utilizada nem no Swagger publicado
consultado nesta entrega. O dropdown depende da entrega de colaboradores
(SCRUM-517). GET /roles e GET /units são reutilizados para a edição.
