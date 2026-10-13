# API de colaboradores — SCRUM-517

As rotas administrativas usam `Authorization: Bearer <token Firebase>` e
`ApiResult`. O solicitante vem de `AuthenticatedUser`; parâmetros arbitrários de
identidade ou workspace não participam da autorização. Somente gestores ativos
dos perfis `GESTOR` e `GESTOR_WORKSPACE` podem consultar colaboradores.

O escopo segue `fn_seleciona_unidades`: ambos os perfis acessam as unidades do
workspace associado à unidade do gestor, conforme a política SQL existente.
Todas as consultas exigem também esse workspace. A view não autoriza o acesso.

## Listagem — SCRUM-518

`GET /collaborators` retorna a lista completa, ordenada por nome e ID, incluindo
`PRE_CADASTRADO`, `ATIVO` e `DESATIVADO`. Não exige busca, filtros ou paginação.
O resultado em `data` contém `id_usuario`, `nome`, `email`, `cargo_id`, `cargo`,
`unidade_id`, `unidade` e `status`. Cada colaborador aparece uma vez. A ausência
de colaboradores autorizados resulta em `data: []`. CPF, senha e Firebase UID
não fazem parte desse contrato.

## Detalhes — SCRUM-519

`GET /collaborators/{collaboratorId}` consulta a mesma view e o mesmo escopo.
Retorna os campos da listagem e acrescenta `cpf`, `modalidade` e `workspace_id`.
A modalidade pode ser nula para cadastros antigos. Registros inexistentes, gestores
e colaboradores de outro workspace retornam o mesmo erro 404, sem revelar sua
existência. IDs não positivos retornam 400. A rota atende informações e edição,
sem alterar o endpoint de perfil próprio `/user/me`.

## Dependências

A [view vw_colaboradores](https://github.com/Astro-Inter/banco-de-dados/pull/32)
e a função existente `fn_seleciona_unidades(TEXT)` precisam estar implantadas no
PostgreSQL conectado à API. Esta entrega não cria objetos SQL.

## Erros

Os erros seguem o tratamento global da API: parâmetros e dados inválidos retornam
400, perfil sem permissão retorna 403, recursos fora do escopo retornam 404,
conflitos retornam 409 e indisponibilidade de dados retorna 503. A segurança
existente rejeita requisições sem autenticação válida antes do controller.
