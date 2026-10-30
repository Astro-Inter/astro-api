# Home do colaborador e do gestor

As rotas abaixo usam `Authorization: Bearer <Firebase ID token>` e retornam
`ApiResult`. O usuário é identificado pelo UID do token; não há parâmetros de
escopo enviados pelo cliente. Para indicadores do workspace, consulte
[Home do workspace](home-workspace.md).

## Colaborador

`GET /home/collaborator` aceita `COLABORADOR`, `GESTOR` e `GESTOR_WORKSPACE`.
O objeto `data` contém:

| Campo | Conteúdo |
| --- | --- |
| `username`, `profilePhotoUrl`, `workspace_name` | Nome, URL temporária da foto e nome do workspace. |
| `events_metrics` | Contadores `pending`, `under_analysis` e `done`. |
| `status_message` | Indicador `URGENT`, `PENDENT`, `ANALYSIS` ou `OK`. |
| `pending_events` | Até três itens com `id`, `title`, `limit_date` e `viewed`. |
| `last_notifications` | Até três itens com `id`, `title`, `sent_at_in_hours` e `viewed`. |

Os eventos e métricas vêm da função consultada no PostgreSQL; notificações vêm
do MongoDB. A Home usa a mensagem da notificação no campo `title`. A leitura
não marca itens como visualizados: veja [notificações](api-notifications.md) e
[conclusão de eventos](api-event-completions.md) para as rotas correspondentes.
Usuário inexistente ou inelegível retorna `404`.

Na definição da função disponível no repositório do banco, os eventos
cancelados são excluídos e as pendências expiradas são ignoradas. A função conta
cada evento uma vez e prioriza pendência sobre análise e conclusão. `URGENT`
indica pendência com prazo em até três dias; os demais estados seguem a ordem
`PENDENT`, `ANALYSIS`, `OK`. Datas seguem a sessão PostgreSQL. Confirme a versão
da função instalada no ambiente.

## Gestor

`GET /home/manager` aceita `GESTOR` e `GESTOR_WORKSPACE`. O objeto `data`
contém `username`, `profilePhotoUrl`, `workspace_name`, `pending_validations`,
`pending_forms` e `indicators`.

Cada validação contém `id`, `participantId`, `eventId`, `collaborator_name`,
`event_title` e `viewed`. `participantId` identifica o usuário participante e
`eventId` identifica o evento associado à conclusão pendente.
Cada formulário contém `id`, `title`, `days_left` e `viewed`; são consultados
até três formulários pendentes. `days_left` é a diferença em dias de calendário
até o prazo, no fuso `America/Sao_Paulo`; pode ser negativo ou nulo. Os
indicadores são `current_events`, `pending_validations` e
`collaborators_with_pending_events`. O banco fornece indicadores e validações;
os formulários são consultados no MongoDB.

Na função do banco disponível no repositório, as validações vêm dos eventos não
cancelados criados pelo gestor e são limitadas às três mais antigas; o indicador
conta todas. Eventos atuais são eventos ativos com turma em andamento. A contagem
de pessoas com pendências considera usuários distintos de eventos atuais. Esses
cálculos dependem da definição SQL implantada.

Gestor inexistente, inativo ou inelegível retorna `404`. Falta de permissão
retorna `403`; indisponibilidade dos dados pode retornar `503`. As respostas
podem apresentar URL de foto nula quando não há foto ou URL válida. Falhas na
consulta de foto não são tratadas aqui como nulas em todos os casos.

## Verificação

Os testes existentes incluem `HomeControllerTest`, `HomeAuthorizationTest`,
`ManagerHomeControllerTest`, `ManagerHomeAuthorizationTest` e testes de serviço.
Integrações simuladas não comprovam o estado dos serviços implantados.
