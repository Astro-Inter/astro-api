# API de colaboradores — SCRUM-517

Os endpoints de colaboradores pertencem ao módulo `user`. Controller, DTOs,
mapper, repository, projections, service e política de status ficam nos pacotes
`com.astro.api.user`, junto aos demais recursos de usuários. Os testes seguem a
mesma organização. As rotas públicas permanecem em `/collaborators`.

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
Inclui usuários dos tipos `COLABORADOR`, `GESTOR` e `GESTOR_WORKSPACE`, conforme
`vw_colaboradores`. A tela de colaboradores envia `?excludeSelf=true` para ocultar
o próprio usuário autenticado, antes da consulta de fotos. O padrão `false`
preserva a própria conta nos seletores de conformidades e participantes.
O parâmetro não altera o workspace nem as unidades autorizadas.
O resultado em `data` contém `id_usuario`, `nome`, `email`, `cargo_id`, `cargo`,
`unidade_id`, `unidade`, `status`, `profilePhotoUrl`, `avatarInitials` e `canManage`.
Cada colaborador aparece uma vez. A ausência
de colaboradores autorizados resulta em `data: []`. CPF, senha e Firebase UID
não fazem parte desse contrato.

## Resumo para a Home do gestor

`GET /collaborators/pending-count` retorna os dados compactos dos colaboradores
para a Home do gestor autenticado. A identidade e o workspace vêm do token e dos
relacionamentos no banco; não aceite ID de usuário ou workspace enviado pelo
cliente para compor o escopo. Inclui `COLABORADOR`, `GESTOR` e
`GESTOR_WORKSPACE` das unidades autorizadas e exclui o próprio gestor. Mantém
os status presentes na listagem normal; usuários inativos ou pré-cadastrados
recebem contagem zero.

Exemplo de `data`:

```json
[
  {
    "id": 12,
    "name": "Ana Silva",
    "profilePhotoUrl": "https://arquivos.example.com/foto-assinada",
    "roleName": "Engenheira",
    "pendingCount": 2
  }
]
```

`pendingCount` conta eventos distintos por colaborador com a mesma
regra da Home mobile: eventos cancelados e participações com prazo expirado são
ignorados; pendência em qualquer turma do evento prevalece sobre envio em
análise ou conclusão validada. Eventos em análise não entram na contagem. A
lista é ordenada por nome e ID e pode ser vazia. A resposta usa o envelope
`ApiResult`; a foto pode ser nula.

A contagem e os dados do colaborador vêm de `fn_carrega_colaboradores(TEXT)` no
Banco de Dados, provisionada externamente a este repositório. Garanta que a
função esteja instalada no banco antes de implantar esta versão da API; a URL da
foto continua sendo resolvida pela API.

## Detalhes — SCRUM-519

`GET /collaborators/{collaboratorId}` consulta a mesma view e o mesmo escopo.
Retorna os campos da listagem e acrescenta `cpf`, `modalidade` e `workspace_id`.
A modalidade pode ser nula para cadastros antigos. Os três tipos de usuário podem
ser consultados. Registros inexistentes e usuários fora do escopo retornam 404, sem revelar sua
existência. IDs não positivos retornam 400. A rota atende informações e edição,
sem alterar o endpoint de perfil próprio `/user/me`.

## Perfil por ID

`GET /collaborators/{collaboratorId}/profile` retorna `id`, `name`, `roleName`,
`profilePhotoUrl`, `unitName`, `workModel`, `email`, `pendingCount` e `nrs` para
um usuário do tipo `COLABORADOR`, `GESTOR` ou `GESTOR_WORKSPACE`. Exige gestor
ativo autenticado e aplica o mesmo workspace e as unidades autorizadas de
`GET /collaborators/{collaboratorId}`. IDs não positivos retornam 400; usuário
inexistente ou fora do escopo retorna 404.

`pendingCount` segue a contagem da Home do gestor em
`fn_carrega_colaboradores(TEXT)` e retorna a contagem do ID solicitado. A função
existente já implementa a regra de eventos atribuídos ainda pendentes; não é
necessário criar uma função SQL nova para este contrato. `nrs` usa o mesmo formato de `/user/me`:
`code`, `validade`, `descricao`, `objetivo` e `aplicabilidade`. A validade vem
da conformidade mais recente do usuário para cada NR vinculada ao cargo. NRs
revogadas não são retornadas. A foto usa o serviço de avatar já compartilhado
pelos demais endpoints de colaboradores.

## Eventos por colaborador

`GET /collaborators/{collaboratorId}/events` retorna as participações do
colaborador em turmas de eventos, ordenadas pelo prazo da turma mais recente
primeiro. A consulta aplica o mesmo workspace e as unidades autorizadas dos
outros endpoints administrativos. Colaborador inexistente ou fora do escopo
retorna 404; ID não positivo retorna 400. A lista pode ser vazia.

O objeto em `data` contém `collaboratorId`, `collaboratorName` e `events`. Cada
item de `events` contém `eventId`, `eventTitle`, `classLimitDate`,
`completionStatus`, `eventStatus` e `completionMode` (`COLABORADOR` ou `GESTOR`). Eventos cancelados e concluídos permanecem
na resposta. `completionStatus` segue os valores de
`ParticipationStatus`: `PENDENTE` quando não há conclusão registrada,
`EM_ANALISE` quando existe uma conclusão `PENDENTE`, além de `CONCLUIDO` e
`REJEITADO`. `eventStatus` segue `EventStatus`.
Os dados são fornecidos por `fn_carrega_eventos_colaborador(TEXT, BIGINT)`,
provisionada fora do repositório da API. A função deve estar instalada no banco
antes de implantar esta versão.

## Cadastro — SCRUM-520

`POST /collaborators` recebe todos os campos obrigatórios:

```json
{"name":"Ana Silva","cpf":"529.982.247-25","email":"ana@example.com","roleId":5,"unitId":12,"workModel":"HIBRIDO"}
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

`PUT /collaborators/{collaboratorId}` exige `name`, `cpf`, `email`, `roleId`,
`unitId`, `workModel` e `status`. Cadastro e edição usam os nomes dos campos
diretamente, sem `@JsonProperty`.
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
permanece registrado para intervenção. A contenção afeta somente a conta editada;
as demais contas são preservadas. A atualização de contenção usa uma query JPQL
condicionada ao ID e ao Firebase UID, válida para os três perfis. Uma consulta
derivada de leitura não substitui essa atualização condicional. A consulta de
edição permanece nativa por usar `fn_seleciona_unidades` e `FOR UPDATE OF u`.

## Status — SCRUM-522

`PATCH /collaborators/{collaboratorId}/status` recebe explicitamente:

```json
{"status":"DESATIVADO"}
```

Aceita somente `ATIVO` e `DESATIVADO`. Campos ausentes, nulos, valores booleanos
ou `PRE_CADASTRADO` retornam 400. Repetir o status atual retorna 200 sem nova
gravação; a rota não alterna status automaticamente. O PUT usa a mesma política.

É permitido desativar pré-cadastros. A ativação exige cadastro concluído, UID
não vazio, cargo e unidade ativos do workspace. Não conclui pré-cadastro e não
permite voltar a ele. Ao ativar, consultas escalares recentes com `FOR SHARE`
protegem os vínculos contra desativação concorrente; o usuário permanece bloqueado
com `FOR UPDATE`. A resposta contém os detalhes no envelope `ApiResult`.

O filtro existente consulta `AuthorizationService` em cada requisição, que exige
`UserStatus.ATIVO`. Assim, o mesmo token Firebase é rejeitado na próxima operação
protegida após a desativação local. Não é necessário desativar a identidade no
Firebase, e o colaborador pode voltar a ser ativado quando elegível.
O gestor do workspace pode editar e alterar o status dos três tipos de usuário
no seu escopo. O gestor comum pode administrar colaboradores e a própria conta,
mas recebe 403 ao tentar editar ou alterar o status de outro gestor ou do gestor
do workspace. Todos continuam consultáveis no escopo, com `canManage=false`
quando a administração não for permitida. Fora do workspace ou das unidades
autorizadas, o retorno permanece 404. A permissão é conferida na API após a
busca com bloqueio e antes de qualquer alteração no banco ou no Firebase.

`canManage` é calculado a partir da identidade autenticada e do tipo persistido
do alvo; não é aceito como autorização no corpo da requisição. O WEB usa esse
campo para mostrar ou ocultar as ações de edição e status. Campo ausente é
tratado como somente leitura. Consultar não autoriza gravar. O tipo do usuário
é preservado, e transferência da gestão do workspace continua no fluxo próprio.

## Dependências

A [view vw_colaboradores](https://github.com/Astro-Inter/banco-de-dados/pull/32)
e a função existente `fn_seleciona_unidades(TEXT)` precisam estar implantadas no
PostgreSQL conectado à API. Esta entrega não cria objetos SQL.

## Fotos e cache de avatares

Listagem, detalhes, edição e alteração de status incluem `profilePhotoUrl` e
`avatarInitials`. O cadastro retorna URL nula, pois o novo usuário ainda não tem
foto. A origem das imagens é `usuario_foto_perfil`: `usuario_id` identifica o
usuário e `caminho_objeto` identifica o arquivo no bucket Cloudflare R2. O mesmo
`UserAvatarService` atende essas consultas e o perfil mobile `GET /user/me`.

Depois de autorizar os colaboradores, a listagem consulta as chaves do Redis em
um único `MGET`. As fotos ausentes do cache são consultadas no PostgreSQL em lote
com `findAllById`, evitando uma consulta por colaborador. As URLs são assinadas
pelo presigner R2 existente e gravadas em pipeline. A assinatura ocorre localmente;
o navegador usa a URL para carregar o arquivo no Cloudflare.

Cada chave `user:avatar:{id_usuario}` contém apenas uma string com a URL. A
expiração absoluta da chave coincide com a expiração da assinatura, usando
`SET PXAT`; o tempo de geração e de comunicação não prolonga a validade no cache.
O período segue `R2_PRESIGNED_URL_DURATION` (padrão `1d`, por exemplo `3d`, máximo
`7d`), sem configuração duplicada. Consultar uma URL em cache não renova seu TTL.
Depois da expiração, a próxima consulta gera uma nova URL. Falhas de leitura ou
gravação no Redis permitem consultar o banco e devolver uma URL recém-gerada.

O upload em `PUT /user/me/profile-photo` invalida a chave depois de persistir a
nova foto e antes de excluir o objeto antigo. Se a persistência falhar, o cache
anterior é preservado e o novo objeto é removido conforme o fluxo existente.
Falhas ao invalidar a chave são reportadas pelo tratamento de erros da API.

Sem foto, `profilePhotoUrl` é `null`; nenhuma URL de placeholder é armazenada no
Redis. `avatarInitials` contém as iniciais do primeiro e do último nome, em
maiúsculas (`Bruno Martins` → `BM`; nome único → uma inicial). O frontend deve
usar a imagem quando houver URL e mostrar essas iniciais no círculo existente
quando a URL for nula, incluindo o fallback se a imagem falhar ao carregar.

`UserAvatarRedisTest` usa somente Redis descartável em loopback quando
`AVATAR_TEST_REDIS_PORT` é informada. Verifica o tipo string, TTL, reutilização,
invalidação e regeneração após expiração real, sem usar Redis compartilhado.
Nesta máquina Windows, a validação usa
`-DargLine=-Djdk.net.unixdomain.tmpdir=target/unavailable-unix-socket-directory`,
com esse diretório ausente, para que o JDK use seu fallback TCP nos sockets
internos do seletor. Esse ajuste é apenas do comando local de testes.

## Erros

Os erros seguem o tratamento global da API: parâmetros e dados inválidos retornam
400, perfil sem permissão retorna 403, recursos fora do escopo retornam 404,
conflitos retornam 409 e indisponibilidade de dados retorna 503. A segurança
existente rejeita requisições sem autenticação válida antes do controller.

## Validação e reprodução

`mvn --batch-mode --no-transfer-progress verify` executa os testes locais e gera
o JAR. Os testes HTTP usam MockMvc com o filtro Firebase e a segurança reais,
substituindo serviços externos por mocks. Incluem rejeição do mesmo token após
desativação e preservação do acesso do gestor. Falhas de atualização, commit e
compensação de e-mail são simuladas; não representam mutações em Firebase real.

`CollaboratorPostgresTest` executa apenas quando `COLLABORATOR_TEST_JDBC_URL`
aponta explicitamente para `jdbc:postgresql://127.0.0.1:<porta>/scrum517` ou
`localhost`, em um banco descartável. Instale nele os scripts oficiais existentes
`1_create_table.sql`, `2_constraints.sql`, `04_vw_unidades.sql`,
`05_vw_colaboradores.sql` e `15_fn_seleciona_unidades.sql` do repositório do banco.
O usuário desse banco de teste é `postgres`, sem senha; restrinja o container ao
loopback. Os testes não criam objetos SQL. As transações de leitura/cadastro
fazem rollback, e os testes de atualização removem seus registros em `finally`.

A validação desta entrega usou PostgreSQL 17 isolado com esses objetos oficiais,
incluindo consultas, projections, constraints únicas, preservação de campos,
edição, status e rollback diante de falha simulada do Firebase. O build final
executou 206 testes: nenhuma falha/erro e um teste de contexto já desabilitado
no projeto. A verificação somente de leitura no banco configurado confirmou a
existência da view e da função; não executou alterações nem validou os endpoints
novos nesse ambiente. Os endpoints precisam ser implantados antes de testes HTTP
no ambiente publicado. Os dropdowns continuam usando `/roles` e `/units`.

## Reenvio da chave de acesso — SCRUM-672

`POST /collaborators/{collaboratorId}/access-key/resend`, sem corpo, exige Bearer Firebase de GESTOR ou GESTOR_WORKSPACE ativo. O destinatário vem do registro consultado dentro do workspace e das unidades autorizadas, sob bloqueio da linha. Não aceita e-mail ou identidade enviados pelo cliente. Exige tipo COLABORADOR, status PRE_CADASTRADO e Firebase UID ausente; não altera status, vínculos ou identidade.

Inclui o e-mail na lista Redis `processamento:email:colaborador`, reutilizando o processamento do cadastro. A API não gera ou expõe tokens, não altera TTL e não confirma entrega do e-mail. A geração/envio e o prazo informado de sete dias permanecem no consumidor existente, externo a este repositório.

Retorna 202 com `ApiResult<Void>` (data null) apenas após confirmação do Redis. Retorna 400 para ID ou estado inválido, 401/403 sem autenticação válida ou sem perfil/permissão, 404 para registro inexistente ou fora do escopo, 503 para falha de conexão com Redis. Outras falhas seguem o handler global. Redis e PostgreSQL não compartilham transação; erro de rede após a gravação pode deixar o resultado do enfileiramento indeterminado. Repetir a solicitação pode reenfileirar o mesmo e-mail.
