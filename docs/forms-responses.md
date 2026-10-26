# Formulários: respostas mobile e análise web

Complementa [forms.md](forms.md). Subtarefas da SCRUM-548: SCRUM-611 a SCRUM-616.
Referências funcionais: SCRUM-272, SCRUM-273, SCRUM-275, SCRUM-276, SCRUM-278,
SCRUM-342 e SCRUM-610. Os detalhes incluem as coordenadas da unidade vinculada
para comparação no front. O envio das respostas não recebe GPS. A verificação de presença fica no Android,
com ou sem unidade definida conforme as regras descritas abaixo.

## Autenticação e escopo

Todas as rotas exigem `Authorization: Bearer <token Firebase>` e perfil `GESTOR`
ou `GESTOR_WORKSPACE`. `FormAccessService` confere a identidade, o perfil atual,
a conta ativa, a unidade ativa e o workspace. IDs de usuário/workspace vindos
do cliente não definem o escopo. `/managers` mantém suas permissões existentes.

A caixa de recebidos e o envio de respostas são exclusivos do destinatário,
inclusive quando a conta é de gestor de workspace. Para consultar respostas e
imagens, são participantes autorizados: destinatário, remetente e gestor do
mesmo workspace. Apenas remetente e gestor do workspace podem analisar.
Rascunhos não aparecem ao destinatário. Recurso inexistente ou fora do escopo: 404.
Nas novas rotas desta entrega, token ausente/inválido retorna 401; falta de perfil,
conta/unidade elegíveis ou permissão retorna 403, ambos com envelope ApiResult.
As rotas anteriores de formulários e os demais módulos mantêm seus comportamentos.

## Rotas

| Jira | Endpoint | Finalidade |
| --- | --- | --- |
| SCRUM-611 | `GET /forms/received` | Caixa de formulários recebidos do gestor autenticado |
| SCRUM-612 | `GET /forms/received/{id}` | Perguntas, metadados, respostas atuais e política de GPS |
| SCRUM-613 | `POST /forms/{id}/responses` | Envio final único das respostas e imagens |
| SCRUM-614 | `GET /forms/{id}/responses` | Consulta das respostas e da análise no web/mobile |
| SCRUM-615 | `POST /forms/{id}/review` | Aprovação ou reprovação definitiva |
| SCRUM-616 | `GET /forms/{id}/images/{imageId}` | URL temporária de uma imagem do formulário |

Os sucessos usam HTTP 200 e o envelope `success`, `message`, `data`, `errors`,
`path` já existente. IDs de formulário/imagem são strings; IDs de gestores,
perguntas e opções são numéricos. As rotas literais `/received` e `/recipients`
têm precedência sobre `/forms/{id}`. IDs de formulário inválidos: 400.
Envio com Content-Type não suportado nas novas rotas: 415 com ApiResult.
Upload acima do limite da requisição: 413. Os status estão documentados no OpenAPI.

### Listagem de recebidos

`GET /forms/received?search=inspeção&nrCode=6&status=PENDENTE&page=0&size=20`

Filtros opcionais: `search` literal no título/descrição, até 240 caracteres;
`nrCode` positivo; `status` igual a `PENDENTE` ou `CONCLUIDO`. Para todos os
recebidos, omita status. Página começa em zero; tamanho entre 1 e 100.
Ordenação: criação e ID decrescentes. Não carrega respostas na listagem.

`data` contém `items`, `page`, `size`, `totalElements`, `totalPages`.
Cada item tem `id`, `title`, `description`, `nrCode`, `unitId`, `senderManagerId`,
`dueDate` (`YYYY-MM-DD`), `createdAt`, `respondedAt`, `questionCount`, `status`,
`reviewStatus` e `viewed` (boolean). Lista vazia: sucesso com `items: []` e totais zero.
Sem filtro de status, inclui pendentes e concluídos, inclusive aprovados e reprovados.
Não existe status CANCELADO: reprovação é `status: CONCLUIDO` e `reviewStatus: rejected`.
`viewed` é individual por usuário e reutiliza ViewedService (Redis), com a mesma chave
`forms` da home. Abrir `GET /forms/received/{id}` marca como visualizado somente após
validar o acesso e obter os detalhes. Listar não marca itens como visualizados.
Parâmetros adicionais como `managerId=999&workspaceId=999` não alteram o escopo.

### Detalhes e respostas

`GET /forms/received/{id}` funciona antes e depois do envio, exclusivamente para
o destinatário. `GET /forms/{id}/responses` exige envio concluído (antes: 409)
e segue a visibilidade dos participantes. Ambos retornam o mesmo formato em `data`:

```json
{
  "form": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "workspaceId": 1,
    "senderManagerId": 10,
    "managerId": 20,
    "title": "Inspeção de segurança",
    "unitId": 3,
    "unitLocation": {"latitude": -23.5, "longitude": -46.6},
    "status": "CONCLUIDO",
    "questions": [
      {"id": 1, "type": "texto", "question": "Descreva o local", "required": true, "answer": ["Condição segura"]}
    ]
  },
  "senderName": "Gestor remetente",
  "recipientName": "Gestor destinatário",
  "unitName": "Sede",
  "locationPolicy": {"captureRequired": false, "enforceUnitRadius": false, "radiusMeters": 100, "maxReadingAgeSeconds": null},
  "locations": [],
  "review": {"status": "awaiting_review"},
  "canReview": true
}
```

O exemplo abrevia os campos de `form`: esse objeto mantém o contrato completo
de `FormDetailsResponse`, descrito em `forms.md`. Nomes inexistentes retornam
string vazia. `unitName` refere-se à unidade contextual do formulário, quando definida.
`review.status`: `awaiting_response`, `awaiting_review`, `approved`, `rejected`.
`canReview` considera a conta autenticada e só é verdadeiro após envio e antes da decisão.
Na análise definitiva, `review` também contém `reviewerManagerId`, `reviewerName`
e `reviewedAt`. `description` só aparece quando reprovado.

### Envio final

Sem imagens, envie `Content-Type: application/json`:

```json
{
  "answers": [
    {"questionId": 1, "text": "Condição segura"},
    {"questionId": 2, "optionIds": [1]},
    {"questionId": 4, "date": "2026-10-24"}
  ]
}
```

Com imagens, use `multipart/form-data` com uma parte `response` do tipo
`application/json`, contendo somente `answers`, e partes repetidas `files` contendo
os binários. Para responder uma pergunta de foto, inclua em `answers`:
`{"questionId":3,"fileIndexes":[0,1]}`. Os índices começam em zero e se referem à
ordem global das partes `files`; cada arquivo pertence a uma única pergunta.
Não envie chaves do bucket, caminhos locais ou URLs como respostas de imagem.
O upload acontece junto com o envio final; não existe endpoint separado para anexos de rascunho.

Regras:

- Formulário `PENDENTE`, ainda sem envio/respostas. Segundo envio ou corrida perdida: 409.
- Perguntas obrigatórias precisam de resposta. Opcionais podem ser omitidas e ficam com `answer: []`.
- IDs de pergunta/opção devem pertencer ao formulário, sem repetições.
- Envie apenas os campos do tipo da pergunta: `text`, `optionIds`/`otherText`, `date` ou `fileIndexes`.
- Texto até 10000 caracteres; `otherText` até 1000, obrigatório somente se a opção Outros for selecionada.
- Escolha simples aceita uma opção; múltipla respeita a definição da pergunta.
- Fotos respeitam `maxFiles` e `maxFileSizeMb` da pergunta. O servidor também limita cada arquivo a 50 MB e a requisição multipart a 151 MB.
- Apenas JPEG e PNG decodificáveis, até 32 milhões de pixels. O servidor verifica os bytes; MIME/extensão declarados não bastam. A câmera mobile atual gera JPEG.
- Não envie `locations` nem `locationConsent`: esses campos foram removidos do DTO. Não há validação de coordenadas, precisão, horários ou consentimento no envio.
- A verificação do raio de 100 metros é feita no Android antes do início e a cada minuto durante o preenchimento. Nenhum registro de GPS é enviado à API.
- Com `unitId` e `unitLocation: null`, o Android bloqueia o início e informa a indisponibilidade. Sem `unitId`, não há restrição de endereço.
- O prazo não bloqueia a resposta: um formulário ainda PENDENTE pode ser respondido após `dueDate`.

O servidor preserva perguntas/opções, grava as respostas no mesmo documento,
define `respondido_em`, incrementa a versão e muda o status para `CONCLUIDO` em
uma única operação condicional no Mongo. Os dados do formulário ficam protegidos
contra edição e exclusão pelas rotas existentes. Sem endpoint de reenvio.

### Análise definitiva

Aprovar: `POST /forms/{id}/review` com `{"status":"approved"}`.

Reprovar: mesmo endpoint com
`{"status":"rejected","description":"As imagens não permitem validar a inspeção."}`.

Reprovação exige descrição não vazia de até 1000 caracteres. Aprovação recusa
o campo `description` se vier preenchido ou como string vazia. Omita-o.
Apenas respostas concluídas podem ser analisadas. Decisão anterior ou corrida
perdida retorna 409. A reprovação encerra o fluxo, sem novo envio.
O status principal permanece `CONCLUIDO`; a decisão fica em `review.status`.

### Imagens privadas

As respostas de foto contêm metadados públicos:
`{"id":"<uuid>","name":"camera.jpg","contentType":"image/jpeg","sizeBytes":12345}`.
Chaves internas e URLs assinadas não são expostas nos detalhes nem persistidas como URL.
Para visualizar cada imagem, chame `GET /forms/{id}/images/{imageId}`. O ID deve
estar no documento do formulário autorizado; IDs de outro formulário retornam 404.

`data` retorna `id`, `url` e `expiresAt`. A URL é assinada por no máximo 15 minutos
(ou pela duração R2 configurada, se menor); o endpoint envia `Cache-Control: no-store`.
Ao expirar, solicite outra URL com o token Firebase. O bucket não usa acesso público.

## Configuração do bucket

No `.env` local e no ambiente de execução da API:

```dotenv
R2_BUCKET_FORM_IMAGES=astro-form-images
```

`storage.r2.form-images-bucket` lê essa variável. O `.env.example` contém o nome;
o `.env` real permanece ignorado pelo Git. Reutiliza `R2_ENDPOINT`,
`R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` e os beans S3 existentes.
As credenciais precisam permitir leitura/escrita/exclusão no novo bucket.
Nenhum token ou segredo é incluído no código ou nesta documentação.

Os objetos usam chaves geradas pelo servidor em
`formularios/{formId}/perguntas/{questionId}/{imageUuid}.jpg` (ou `.png`).
Em falha anterior à gravação ou conflito conhecido, o serviço remove os uploads
da tentativa. Se a confirmação do Mongo se perder após a gravação, tenta recuperar
o documento pelo `id_envio`. Sem confirmação possível, preserva as imagens e
registra os IDs para reconciliação, evitando apagar anexos de uma resposta possivelmente salva.

## Geolocalização da unidade

`GET /forms/{id}` retorna `data.unitLocation`. Nos detalhes usados pelo mobile,
`GET /forms/received/{id}`, e na consulta das respostas, `GET /forms/{id}/responses`,
o mesmo campo fica em `data.form.unitLocation`:

```json
"unitLocation": {
  "latitude": -23.5,
  "longitude": -46.6
}
```

As coordenadas são obtidas de `unidade_endereco.geolocalizacao` da unidade vinculada
ao formulário salvo, dentro do workspace autorizado. Parâmetros adicionais ou
coordenadas enviados pelo cliente não substituem essa referência. Sem unidade,
sem coordenadas válidas ou sem vínculo nesse workspace, `unitLocation` é `null`.
Com unidade vinculada e coordenadas ausentes, o Android bloqueia o início e informa
a indisponibilidade. Sem unidade vinculada, não há restrição de endereço.

Os detalhes de recebidos/respostas também mantêm o campo de política:

```json
"locationPolicy": {
  "captureRequired": false,
  "enforceUnitRadius": false,
  "radiusMeters": 100,
  "maxReadingAgeSeconds": null
}
```

`radiusMeters` é a referência de 100 metros quando há unidade; sem unidade, é `null`.
`captureRequired: false` indica que não existe captura obrigatória para envio à API.
`enforceUnitRadius: false` indica que a API não compara distância; essa regra é do Android,
antes de iniciar e a cada minuto. `maxReadingAgeSeconds` é `null`.

A consulta reutiliza `UnitRepository`, com `@Query` nativa e parâmetros nomeados,
retornando `UnitLocationProjection`. Ela extrai X como longitude e Y como latitude;
`FormLocationService` coordena a consulta e o mapper monta o DTO. Não exige PostGIS.
A coluna `unidade_endereco.geolocalizacao POINT` precisa existir no banco de execução,
conforme o [schema oficial](https://github.com/Astro-Inter/banco-de-dados/blob/main/database/scripts/1_create_table.sql).
Não há migração ou preenchimento automático de coordenadas.

Novos envios não recebem GPS nem calculam ou gravam `validacao_localizacao`; `locationCheck` é `null`.
Registros históricos que já possuírem essa comparação continuam legíveis e preservados.
As coordenadas de `unitLocation` refletem o cadastro atual, não o GPS informado pelo gestor.

## MongoDB e camadas

Continua existindo apenas a collection `forms`; não são criadas collections de
sequência, respostas, análises ou anexos. Novos envios usam `id_envio` (UUID interno para recuperação) e `analise`.
Não gravam `consentimento_localizacao`, `localizacoes` nem `validacao_localizacao`.
Dados históricos de GPS permanecem legíveis, sem migração destrutiva.
O campo histórico `validacao_localizacao` é preservado na leitura, mas não é criado nos novos envios.
Análise usa `status: APROVADO|REPROVADO`, `id_gestor_revisor`, `analisado_em`.
**`analise.descricao` só existe em reprovação**; antes da análise, `analise` é ausente.
Fotos ficam em `perguntas[].resposta[]` com `id_imagem`, `chave_objeto`, `nome`,
`tipo_conteudo`, `tamanho_bytes`. IDs numéricos continuam compactados para BSON
int32 quando estiverem entre -2.147.483.648 e 2.147.483.647, preservando int64 fora dessa faixa para não perder precisão. No Mongo, Long(...) representa o tipo BSON int64; o contrato JSON continua retornando números. Atualizações parciais não normalizam automaticamente campos históricos que não são alterados. Não há migração destrutiva.

`FormResponseService` coordena o fluxo; `FormAccessService` define o escopo.
As Strategies de resposta validam cada tipo. `FormAnswerMapper` e
`FormResponseMapper` convertem os dados. O fragmento de `FormRepository` apenas
executa consultas e gravações condicionais com os filtros/estados definidos no service.
`FormImageStorageService` retorna o valor interno `PresignedDownload` já existente;
`FormResponseMapper` monta `FormImageDownloadResponse`. A validação e normalização
do UUID são compartilhadas por `FormIdentifierValidator`, usado pelos serviços de
gestão e de resposta. Nomes de participantes são obtidos antes da chamada ao mapper.

Os vínculos e nomes reutilizam `UserRepository` e `UnitRepository`, sem novas
tabelas, consultas SQL manuais ou alterações do cadastro da unidade.

### Organização dos tipos internos

`model` contém o documento `Form`, os valores persistidos dentro dele (`FormReview`,
`FormLocationReading` e o histórico `FormLocationCheck`) e seus enums. Esses valores
embutidos não criam collections próprias.

`FormImage` representa metadados de armazenamento usados entre service e mapper;
não é uma entidade nem um documento Mongo. Ele fica em `dto.internal`, junto com
`FormOtherAnswer` e `FormLocationPolicy`, sem fazer parte do contrato HTTP público.
`FormScope` fica no pacote `service`, pois representa o contexto de autorização.
`FormReadFilter`, `FormWriteCondition` e `FormResponseCondition` ficam em `repository`,
pois descrevem filtros e condições de gravação do acesso aos dados.

## Verificação

`mvn verify` executa a suíte e o build. Com
`FORM_TEST_MONGODB_URI=mongodb://127.0.0.1:27029`, executa também os testes Mongo,
que usam e removem exclusivamente uma base `forms_test_<uuid>`.

Os testes cobrem Strategies, obrigatoriedade, envio JSON e multipart sem GPS,
401/403 nas novas rotas e comportamento legado, viewed individual, pendentes vencidos,
concluídos/reprovados, coordenadas nos detalhes,
coordenadas ausentes, isolamento do workspace, referências de arquivos,
formato real das imagens, bucket configurado, assinatura local, Firebase/perfis,
rotas literais, escopos, concorrência, imutabilidade e omissão da descrição aprovada.
O fluxo HTTP completo usa Mongo local e um armazenamento R2 simulado; não envia
imagens para o bucket real e não usa credenciais de produção.

A integração `UnitRepositoryPostgresTest` usa `UNIT_TEST_JDBC_URL` em uma instância
PostgreSQL local descartável. Ela cria a coluna POINT apenas no schema próprio do
teste e verifica a ordem longitude/latitude, valores nulos e o isolamento do workspace.

A revisão de organização e aderência à skill está em [forms-skill-review.md](forms-skill-review.md).
