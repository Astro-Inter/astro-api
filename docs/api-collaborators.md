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

## Cadastro — SCRUM-520

`POST /collaborators` recebe todos os campos obrigatórios:

```json
{"nome":"Ana Silva","cpf":"529.982.247-25","email":"ana@example.com","roleId":5,"unitId":12,"modalidade":"HIBRIDO"}
```

O nome é aparado, o CPF é validado por dígitos verificadores e armazenado com
11 dígitos, e o e-mail é aparado e convertido para minúsculas, conforme a
importação existente. A modalidade aceita `HIBRIDO`, `REMOTO` ou `PRESENCIAL`.
Campos obrigatórios omitidos ou nulos retornam 400. Cargo e unidade devem existir,
estar ativos, pertencer ao workspace autenticado, e a unidade deve ser autorizada.
Conflitos de CPF e e-mail retornam 409; constraints únicas do PostgreSQL protegem
contra concorrência. A persistência ocorre em transação.

O resultado é 201 com `Location` e os detalhes em `data`, sempre com tipo interno
`COLABORADOR`, status `PRE_CADASTRADO`, data de criação do servidor e UID nulo.
A API não aceita senha nem campos de autenticação no DTO. O evento existente
`PreRegisteredCollaboratorsCreatedEvent` enfileira o e-mail após o commit, usando
o mesmo fluxo Redis de importação; `/verify-key` e `/activate` continuam responsáveis
pela conclusão do cadastro. Uma falha de Redis após commit mantém o comportamento
existente de registrar o erro para reprocessamento operacional.

## Edição — SCRUM-521

`PUT /collaborators/{collaboratorId}` exige os seis campos do cadastro e `status`.
Todos são obrigatórios e não nulos: é uma substituição dos campos editáveis.
O status pode ser mantido como `PRE_CADASTRADO` apenas se já estiver assim; não
é permitido voltar ao pré-cadastro nem concluir o cadastro por esta rota.
ID, tipo, data de criação e Firebase UID são preservados. Conflitos excluem o
próprio colaborador da comparação. Vínculos inativos podem ser mantidos quando
seus IDs permanecem iguais; toda nova vinculação exige cargo/unidade ativos e
do workspace, com autorização sobre a unidade de destino.

A leitura para alteração bloqueia o registro do colaborador no PostgreSQL para
serializar edições concorrentes. A alteração de e-mail de uma conta vinculada
consulta o estado anterior no Firebase, grava e valida os dados locais e atualiza
a identidade antes do commit, marcando o novo e-mail como não verificado.
Falhas incertas de atualização ou de commit compensam o e-mail e sua verificação
no Firebase após o rollback. Conflitos explícitos do Firebase retornam 409.
Uma divergência prévia entre e-mails exige reconciliação antes da edição.

PostgreSQL e Firebase não compartilham uma transação. Se a compensação falhar,
a API tenta desativar somente o colaborador afetado em uma nova transação,
registra o incidente para reconciliação operacional e responde 500. Se o banco
também estiver indisponível, essa contenção não pode ser garantida; o incidente
permanece registrado para intervenção. Não há alterações do gestor solicitante.

## Dependências

A [view vw_colaboradores](https://github.com/Astro-Inter/banco-de-dados/pull/32)
e a função existente `fn_seleciona_unidades(TEXT)` precisam estar implantadas no
PostgreSQL conectado à API. Esta entrega não cria objetos SQL.

## Erros

Os erros seguem o tratamento global da API: parâmetros e dados inválidos retornam
400, perfil sem permissão retorna 403, recursos fora do escopo retornam 404,
conflitos retornam 409 e indisponibilidade de dados retorna 503. A segurança
existente rejeita requisições sem autenticação válida antes do controller.
