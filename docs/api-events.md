# Administração web de eventos — SCRUM-422

Os endpoints usam Firebase e `ApiResult<T>`. Exigem usuário ativo com perfil
`GESTOR` ou `GESTOR_WORKSPACE`. O servidor determina usuário e workspace.
As leituras permitem consultar eventos de outros gestores do mesmo workspace;
escritas exigem o criador, inclusive quando o solicitante é `GESTOR_WORKSPACE`.
Eventos inexistentes ou de outro workspace retornam 404; falta de autenticação,
401; falta de permissão, 403; validação, 400; conflito de integridade, 409.

| Método e rota | Contrato |
| --- | --- |
| `GET /events/{eventId}` | Detalhe completo, autoria, configurações, todas as turmas e participações |
| `GET /events/details/{eventId}/manager-conclusion` | Detalhes para evento com conclusão pelo gestor; inclui quantidade de participantes e prazo da última turma |
| `GET /events/details/{eventId}/collaborator-conclusion` | Detalhes para o gestor acompanhar conclusão pelo colaborador, com métricas de validação e envio |
| `GET /events/{eventId}/participants` | Lista participantes do evento, título e status de conclusão individual |
| `GET /events/participant/{participantId}/manager-conclusion/{eventId}` | Consulta os dados de um participante para conclusão pelo gestor |
| `GET /events/participant/{participantId}/collaborator-conclusion/{eventId}` | Consulta os detalhes enviados pelo colaborador para validação do gestor |
| `POST /events/{eventId}/participants/{participantId}/completion` | Registra a conclusão do participante pelo gestor |
| `GET /events/my-events` | Eventos criados pelo gestor autenticado, com pendências calculadas pela view `vw_eventos_gestor` |
| `GET /events/collaborator/{eventId}` | Detalhes para o colaborador autenticado; consulta sem alterar estado |
| `PATCH /events/collaborator/{eventId}/viewed` | Marca como visualizado um evento de que o usuário autenticado participa |
| `GET /events?startDate=...&endDate=...` | Uma entrada por turma que sobrepõe o intervalo, incluindo status |
| `GET /events/eligible-candidates?nrId=...` | Usuários selecionáveis com Role, unidade e modalidade |
| `POST /events` | Criação atômica do evento ativo, turmas e participações |
| `PATCH /events/{eventId}` | Metadados permitidos e conjunto desejado de turmas |
| `PATCH /events/{eventId}/cancellation` | Cancelamento definitivo com justificativa |

## Detalhe e permissões

`EventResponse` preserva os campos anteriores e acrescenta `managerId`, `canEdit`,
`canCancel` e `detailsEditable`. Cada turma tem `editable`; cada participante
preserva `id` (ID do usuário), `name`, `email` e acrescenta `participationId`
(ID persistido de `turma_colaborador`). Turmas são ordenadas por início e ID;
participantes, por nome e ID do usuário. Listas vazias são `[]`.

- `canEdit`: criador de evento ativo; pode editar/adicionar turmas futuras mesmo
  se todas as turmas existentes já tiverem iniciado.
- `canCancel`: criador de evento ativo, independentemente do início das turmas.
- `detailsEditable`: criador de evento ativo antes do início de qualquer turma.
- `editable`: turma futura de evento ativo consultado pelo criador.

Cancelados e concluídos continuam consultáveis com todas essas permissões falsas.
As flags orientam a interface; a autorização é reaplicada nas escritas.

`GET /events/details/{eventId}/manager-conclusion` é uma resposta própria para o fluxo de conclusão
pelo gestor e não altera o contrato de `GET /events/{eventId}`. Retorna `eventId`,
`eventTitle`, `eventDescription`, `externalLink`, `participantCount`,
`lastClassLimitDate`, `completionMode`, `managerId` e `managerName`.
`participantCount` é a quantidade de usuários distintos participantes;
`lastClassLimitDate` é a maior data de término entre as turmas. Quando não houver
turmas, o prazo é `null`. A validade não faz parte desta resposta.

A rota anterior `GET /events/manager/{eventId}` foi substituída por
`GET /events/details/{eventId}/manager-conclusion`; clientes devem atualizar a URL.

`GET /events/details/{eventId}/collaborator-conclusion` é a visão do gestor para
eventos com `completionMode = COLABORADOR`. Exige gestor ativo (`GESTOR` ou
`GESTOR_WORKSPACE`) e permite consultar eventos de qualquer gestor do mesmo
workspace, inclusive cancelados ou concluídos. Evento fora do workspace ou
inexistente retorna 404; modo de conclusão diferente de `COLABORADOR` ou
identificador não positivo retorna 400.

Retorna todos os campos de `ManagerCompletionModeEventDetailsResponse` e acrescenta:

- `pendingValidationCount`: usuários com conclusão no estado atual `PENDENTE`.
- `validatedCount`: usuários com conclusão `CONCLUIDO` ou `REJEITADO`.
- `notSubmittedCount`: usuários participantes que não têm registro de conclusão.

As métricas contam usuários distintos de todas as turmas do evento, sem filtro de
prazo ou status do participante. Rejeitados permanecem em `validatedCount` até
reenviar; o reenvio muda a conclusão para `PENDENTE`, retirando-a de
`validatedCount` e incluindo-a em `pendingValidationCount`. Não é um histórico de
ações de validação. O fluxo impede o mesmo usuário em turmas diferentes do mesmo
evento. Sem participantes, as contagens são zero; sem turmas, `lastClassLimitDate`
é `null`. A consulta não altera estado.

Exemplo de `data`:

```json
{
  "eventId": 42,
  "eventTitle": "Treinamento",
  "eventDescription": "Orientações de segurança",
  "externalLink": null,
  "participantCount": 10,
  "lastClassLimitDate": "2026-11-05T12:00:00",
  "completionMode": "COLABORADOR",
  "managerId": 7,
  "managerName": "Gestor",
  "pendingValidationCount": 3,
  "validatedCount": 5,
  "notSubmittedCount": 2
}
```

`GET /events/{eventId}/participants` exige perfil `GESTOR` ou `GESTOR_WORKSPACE`
ativo e consulta o evento no workspace autenticado. Retorna uma lista no envelope
`ApiResult`, ordenada por nome e ID do usuário. Cada item contém
`participantName`, `eventTitle` (título do evento) e `participationStatus`:
`PENDENTE` quando ainda não há
solicitação de conclusão, `EM_ANALISE` após envio, `CONCLUIDO` após aprovação e
`REJEITADO` quando devolvido para correção. Também contém `participantId` (ID do
usuário, para consulta individual) e `profilePhotoUrl` com URL temporária da foto
ou `null` quando ausente. Eventos inexistentes ou fora do workspace retornam
404; evento sem participantes retorna lista vazia.

`GET /events/participant/{participantId}/manager-conclusion/{eventId}` exige o
mesmo perfil e escopo de workspace. `participantId` é o ID do usuário
participante. Retorna
`participantId`, `profilePhotoUrl`, `eventTitle`, `participantName`, `roleName`,
`unitName` e `endDate` (término da turma associada), usando `null` quando cargo,
unidade ou foto não estiverem cadastrados. Evento fora do workspace ou usuário
sem participação no evento retorna 404.

`GET /events/participant/{participantId}/collaborator-conclusion/{eventId}` é uma
consulta separada para o fluxo de validação da conclusão enviada pelo
colaborador. Exige gestor ativo e evento no workspace autenticado, configurado
no modo `COLABORADOR`, além de conclusão registrada para o participante.
Retorna
`profilePhotoUrl`, `participantName`, `roleName`, `unitName`, `completionDate`,
`endDate`, `evidenceMandatory`, `evidences` (zero a três itens com URL temporária
de download) e `eventTitle`. Foto, cargo ou unidade podem ser nulos; evento,
participante ou conclusão ausente retorna 404.

`POST /events/{eventId}/participants/{participantId}/completion` é a operação
seguinte ao detalhe individual. Exige gestor ativo, criador do evento e evento
ativo no workspace autenticado, configurado com `completionMode = GESTOR`. Pode
ser executado em qualquer momento enquanto o evento estiver ativo. Registra a
participação com status `CONCLUIDO`, `completionDate` atual e `data_validacao`
no mesmo instante, sem evidência ou validação posterior; a resposta é `201` com `EventCompletionResponse` no envelope
`ApiResult`. Evento em outro workspace ou participante sem vínculo retornam 404;
modo incompatível, evento inativo ou conclusão já registrada retornam 400.

Para evento com NR, `conclusao_evento.data_validade` é a data da conclusão mais
`tempo_reciclagem_meses` do documento publicado da NR no MongoDB. A soma usa meses
de calendário; se o dia não existir no mês de destino, usa o último dia do mês.
Sem NR, a validade é `null`. Documento da NR ausente retorna 404; período ausente
ou não positivo retorna 400. Nessas situações a conclusão não é gravada. Uma
falha de conexão ao banco retorna 503. A validade é persistida na conclusão e não
integra a resposta `EventCompletionResponse`.

## Calendário

`startDate` e `endDate` são obrigatórios e usam datas locais ISO, por exemplo
`2026-11-01T00:00:00`. O fim deve ser posterior ao início. Mantém-se a convenção
inclusiva existente: `turma.endDate >= startDate && turma.startDate <= endDate`.
Uma turma que atravesse qualquer borda aparece no resultado. Workspace, intervalo
e ordenação por início/ID são aplicados no repository, com projection JPQL.
Eventos cancelados permanecem no calendário com `status = CANCELADO`.

## Eventos do gestor autenticado

`GET /events/my-events` exige perfil `GESTOR` ou `GESTOR_WORKSPACE` ativo e retorna
um `ApiResult<List<MyEventResponse>>` com os eventos criados pelo próprio usuário,
em ordem decrescente de `pendingCount`, com ID decrescente como desempate. Inclui eventos
de todos os status e não recebe parâmetros de paginação, filtro ou pesquisa.
Cada item contém `id`, `title`, `status`,
`completionMode` e `pendingCount`.

A consulta usa exclusivamente a view PostgreSQL `vw_eventos_gestor` e filtra
`id_gestor` pelo `id_usuario` resolvido a partir do UID Firebase autenticado. A
view fornece `qtd_pendencias`, mapeada diretamente para `pendingCount`; a API não
recalcula nem altera esse valor. Se não houver eventos, `data` será `[]` no
envelope com HTTP 200. A view ainda precisa ser criada no repositório do banco
antes que a consulta possa ser executada no PostgreSQL.

## Candidatos

Preserva `id`, `name`, `email`, `roleId`, `roleName` e acrescenta `unitId`,
`unitName`, `workModel`. Modalidades canônicas: `PRESENCIAL`, `REMOTO`, `HIBRIDO`;
dados ausentes não são inventados. Apenas usuários ativos do workspace aparecem;
o solicitante é excluído e outros gestores permanecem elegíveis.

Participantes incluem `COLABORADOR`, `GESTOR` e `GESTOR_WORKSPACE`. Ser elegível
para participar não concede permissão de editar o cadastro dessa pessoa. A
restrição cadastral `canManage` não deve ser usada para remover gestores deste
seletor. O criador continua fora do próprio evento, tanto na função SQL quanto
na validação do POST/PATCH. A seleção preserva status ATIVO, workspace e a
associação cargo/NR quando houver NR informada.

`nrId` é o identificador persistido do catálogo, não uma string como `NR 35`.
NR inexistente retorna 404. O repository chama
`fn_seleciona_candidatos_evento(firebaseUid, nrId)` e mapeia os nomes físicos para
a projection. A função SQL `STABLE`, com `SECURITY INVOKER`, deriva o workspace do
gestor ativo, exclui o solicitante, filtra a associação Role/NR por `EXISTS` e
ordena por nome/ID. Não é uma view materializada: retorna o estado atual e não
mantém cache. Services continuam validando autenticação, parâmetros e existência
da NR, e o mapper converte a projection no DTO.

### Instalação do objeto SQL

Antes de implantar esta revisão da API, instale
[`database/functions/18_fn_seleciona_candidatos_evento.sql`](https://github.com/Astro-Inter/banco-de-dados/blob/488f301e5a2b8c2a0f382f3632018f41ae576651/database/functions/18_fn_seleciona_candidatos_evento.sql)
do repositório `Astro-Inter/banco-de-dados` no schema das tabelas Astro. A
[PR #36 do banco](https://github.com/Astro-Inter/banco-de-dados/pull/36) já foi
mesclada. O script é idempotente para a mesma assinatura, não altera tabelas,
triggers ou índices e não é executado automaticamente pela aplicação. O SQL da
função é mantido exclusivamente no repositório do banco e não integra o JAR da API.
A instalação usa uma conexão autorizada para DDL; o usuário da API mantém seus
privilégios de leitura nas tabelas e EXECUTE na função. SECURITY INVOKER conserva
as políticas e permissões de acesso existentes.

O teste PostgreSQL de eventos e seus scripts de preparação foram removidos da API.
O CI não provisiona mais o banco nem faz checkout do repositório de banco para
esse teste. A função de produção continua mantida no repositório do banco, com
instalação separada da API. O Maven não baixa nem instala SQL em produção.

A função foi escolhida porque esta leitura recebe identidade autenticada e NR
opcional. Uma procedure é desnecessária para um SELECT; uma view não recebe esses
parâmetros e exigiria filtros e a regra de elegibilidade na consulta do repository. A mudança
centraliza a consulta, sem afirmar um ganho de desempenho apenas por encurtar o
repository. Na validação original, os testes instalavam o mesmo script em schemas
locais isolados.

## Criação e edição

Título: 3–255 caracteres após trim. Descrição: obrigatória, com pelo menos três
caracteres sem os espaços das extremidades; não há limite artificial para TEXT.
Na criação, a descrição é normalizada; na edição, seu texto exato é preservado
para permitir o reenvio de descrições históricas sem modificar metadados bloqueados.
Link opcional: HTTP(S), sem espaços, máximo 2048; vazio é normalizado para `null`.
Modo de conclusão mantém os valores da API `COLABORADOR` e `GESTOR`.

Deve existir ao menos uma turma e um participante por turma. Nomes de turma têm
até 255 caracteres e são únicos no evento após trim, respeitando a comparação
sensível a maiúsculas da constraint existente. IDs e itens nulos/inválidos são
rejeitados. O término deve ser posterior ao início. Datas passadas continuam
permitidas; não foi introduzido período máximo.

Participantes devem existir no workspace, estar ativos e ser elegíveis para a NR,
quando informada. O criador não participa. O mesmo usuário não pode se repetir na
mesma turma nem entre turmas, incluindo a sobreposição parcial `[1,2]` / `[2,3]`.

O PATCH mantém seu formato completo, com título, descrição e lista de turmas;
não passou a funcionar como patch genérico de campos opcionais. NR, modo de
conclusão, evidência obrigatória e criador não fazem parte dos campos alteráveis.
Depois do início de qualquer turma, título, descrição e link ficam bloqueados.
Turmas iniciadas devem permanecer presentes e inalteradas, incluindo participantes;
sua elegibilidade atual não é exigida para preservar registros históricos.
Turmas futuras podem ser criadas, editadas ou removidas.

`eventClasses` representa o conjunto desejado: um ID existente deve pertencer ao
evento; ID ausente cria turma; turma omitida é removida se futura. Participações
mantidas na mesma turma conservam os IDs e suas conclusões/evidências. Uma
participação com conclusão não pode ser removida. Exclusões são flushadas antes
das inclusões; trocas de nome liberam os nomes dentro da mesma transação antes
da atribuição final, respeitando a constraint única imediata do PostgreSQL.
PATCH e cancelamento bloqueiam a linha do evento com `PESSIMISTIC_WRITE` e usam
`@Transactional`, serializando as escritas do agregado sem alterar o esquema.

## Cancelamento

“Inativar” no web significa cancelamento definitivo. O frontend deve enviar
`{"reason":"Treinamento reagendado"}`. O motivo é normalizado antes de validar
3–1000 caracteres. A operação registra `CANCELADO`, instante e motivo, e retorna
o detalhe com permissões falsas. Não exclui registros nem permite reativar.
Cancelamento repetido ou de evento concluído retorna 400.

## Exemplos e verificação

Swagger usa os schemas dos próprios DTOs para gerar exemplos de requisição e de
`ApiResult`. Títulos, nomes de turma, datas e motivo têm exemplos curtos nas
anotações dos campos; não há uma classe com respostas JSON manuais duplicadas.
`EventOpenApiTest` gera `/v3/api-docs` localmente e verifica operações, respostas,
envelopes, exemplos e schemas. Os dados dos exemplos são fictícios.

Com Java 21, execute:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress verify
```

Permanecem os testes de serviço, segurança, controller e contrato OpenAPI de eventos.
Na validação original, os testes PostgreSQL usavam schemas exclusivos em uma base
local descartável e fixtures com constraints consultadas no catálogo publicado
em modo somente leitura em 14/10/2026. Esses testes e suas fixtures foram removidos.
Nenhum DDL, índice, trigger ou automatização de negócio foi aplicado no ambiente
publicado por essa validação.
Fluxos mobile de conclusão/evidências, aprovação, notificações, conformidade e
encerramento automático permanecem fora desta entrega.
