# Administração web de eventos — SCRUM-422

Os seis endpoints usam Firebase e `ApiResult<T>`. Exigem usuário ativo com perfil
`GESTOR` ou `GESTOR_WORKSPACE`. O servidor determina usuário e workspace.
As leituras permitem consultar eventos de outros gestores do mesmo workspace;
escritas exigem o criador, inclusive quando o solicitante é `GESTOR_WORKSPACE`.
Eventos inexistentes ou de outro workspace retornam 404; falta de autenticação,
401; falta de permissão, 403; validação, 400; conflito de integridade, 409.

| Método e rota | Contrato |
| --- | --- |
| `GET /events/{eventId}` | Detalhe completo, autoria, configurações, todas as turmas e participações |
| `GET /events?startDate=...&endDate=...` | Uma entrada por turma que sobrepõe o intervalo, incluindo status |
| `GET /events/eligible-candidates?nrId=...` | Usuários selecionáveis com Role, unidade e modalidade |
| `POST /events` | Criação atômica do evento ativo, turmas e participações |
| `PATCH /events/{eventId}` | Metadados permitidos e conjunto desejado de turmas |
| `PATCH /events/{eventId}/cancellation` | Cancelamento definitivo com justificativa |

## Detalhe e permissões

`EventResponse` preserva os campos anteriores e acrescenta `managerId`, `canEdit`,
`canCancel` e `detailsEditable`. Cada turma tem `editable`; cada participante
preserva `id` (ID do usuário), `name`, `email` e acrescenta `participationId`
(ID persistido de `turma_funcionario`). Turmas são ordenadas por início e ID;
participantes, por nome e ID do usuário. Listas vazias são `[]`.

- `canEdit`: criador de evento ativo; pode editar/adicionar turmas futuras mesmo
  se todas as turmas existentes já tiverem iniciado.
- `canCancel`: criador de evento ativo, independentemente do início das turmas.
- `detailsEditable`: criador de evento ativo antes do início de qualquer turma.
- `editable`: turma futura de evento ativo consultado pelo criador.

Cancelados e concluídos continuam consultáveis com todas essas permissões falsas.
As flags orientam a interface; a autorização é reaplicada nas escritas.

## Calendário

`startDate` e `endDate` são obrigatórios e usam datas locais ISO, por exemplo
`2026-11-01T00:00:00`. O fim deve ser posterior ao início. Mantém-se a convenção
inclusiva existente: `turma.endDate >= startDate && turma.startDate <= endDate`.
Uma turma que atravesse qualquer borda aparece no resultado. Workspace, intervalo
e ordenação por início/ID são aplicados no repository, com projection JPQL.
Eventos cancelados permanecem no calendário com `status = CANCELADO`.

## Candidatos

Preserva `id`, `name`, `email`, `roleId`, `roleName` e acrescenta `unitId`,
`unitName`, `workModel`. Modalidades canônicas: `PRESENCIAL`, `REMOTO`, `HIBRIDO`;
dados ausentes não são inventados. Apenas usuários ativos do workspace aparecem;
o solicitante é excluído e outros gestores permanecem elegíveis.

`nrId` é o identificador persistido do catálogo, não uma string como `NR 35`.
NR inexistente retorna 404. A associação com Role é filtrada por `EXISTS` em
`cargo_nr`, que não tem entidade JPA no projeto. Essa query nativa específica
retorna projection sem carregar entidades ou duplicar candidatos. A conversão
continua no mapper. Ordenação por nome e ID.

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

Com Java 21 e PostgreSQL 16 local descartável, execute:

```powershell
$env:MANAGER_TEST_DATABASE_URL='jdbc:postgresql://localhost:55422/astro525'
$env:MANAGER_TEST_DATABASE_USER='postgres'
$env:MANAGER_TEST_DATABASE_PASSWORD='astro525-isolated'
.\mvnw.cmd --batch-mode --no-transfer-progress verify
```

Os testes PostgreSQL usam schemas exclusivos em uma base local chamada `astro525`;
o CI já disponibiliza essa infraestrutura. As fixtures reproduzem constraints
consultadas no catálogo publicado em modo somente leitura em 14/10/2026.
Testes de escrita ocorrem exclusivamente no banco isolado. Nenhum DDL, índice,
trigger ou automatização de negócio foi alterado no ambiente publicado.
Fluxos mobile de conclusão/evidências, aprovação, notificações, conformidade e
encerramento automático permanecem fora desta entrega.
