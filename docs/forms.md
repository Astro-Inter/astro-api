# Formulários da web

As rotas usam `Authorization: Bearer <Firebase ID token>` e o envelope `ApiResult`.
Somente gestores ativos, com unidade ativa e workspace válido, têm acesso.
O workspace e o remetente são determinados pela identidade validada.
Os campos de request e response usam os nomes Java em inglês e camelCase,
conforme os componentes dos DTOs, sem `@JsonProperty`.
Clientes devem enviar `title`, `managerId`, `questions`, `dueDate` e os demais
nomes dos exemplos abaixo; os nomes antigos em português não são aliases da API.

## Destinatários para o dropdown

`GET /forms/recipients` retorna os gestores elegíveis para receber um formulário, sem usar a listagem administrativa `/managers`.

Exige token Firebase válido e perfil `GESTOR` ou `GESTOR_WORKSPACE`. O `FormAccessService` valida a conta, o perfil atual, a unidade ativa e o workspace da identidade autenticada. O usuário e o workspace não são parâmetros de escopo desta rota; parâmetros adicionais como `workspaceId`, `currentManagerId`, `managerId`, `unitId` ou `firebaseUid` não alteram a consulta.

O `UserRepository` consulta os usuários no PostgreSQL: somente `GESTOR` e `GESTOR_WORKSPACE` com status `ATIVO`, unidade ativa e pertencente ao mesmo workspace. A própria conta fica de fora. A lista é ordenada por nome e, em caso de empate, por ID. A consulta retorna somente ID, nome e nome da unidade; o mapper monta o DTO de resposta. Os formulários continuam armazenados no MongoDB.

Exemplo de resposta HTTP 200 (IDs ilustrativos):

```json
{
  "success": true,
  "message": "Destinatários retornados com sucesso",
  "data": {
    "currentManagerId": 7,
    "items": [
      {"id": 8, "name": "Ana", "unitName": "Sede"}
    ]
  },
  "errors": null,
  "path": "/forms/recipients"
}
```

`currentManagerId` e `items[].id` são números; `name` e `unitName` são strings. Sem destinatários, a resposta continua sendo sucesso com `items: []` e o ID da conta atual. Sem autenticação válida, perfil permitido ou conta/unidade elegíveis, a chamada é bloqueada conforme a segurança existente da API (HTTP 403).

A rota literal `/forms/recipients` tem precedência sobre `/forms/{id}`; `recipients` não é validado como UUID. O endpoint não altera as permissões administrativas de `/managers`, que continuam reservadas ao gestor do workspace. Responder ou editar formulários continua seguindo as permissões próprias dessas operações.

Os testes HTTP cobrem o envelope, tipos JSON, lista vazia, parâmetros forjados e a distinção da rota UUID. Os testes de segurança cobrem os dois perfis permitidos, token ausente/inválido e perfis proibidos. A integração do repository é executada com `FORM_TEST_JDBC_URL=jdbc:postgresql://127.0.0.1:5434/forms_recipients_test`, usuário `postgres` e senha isolada `forms-recipients-isolated`. Essa base deve ser descartável: o Hibernate cria/remove as tabelas de teste. O CI provisiona uma instância PostgreSQL exclusiva para esse teste, sem executar scripts SQL do projeto.

## Listagem

`GET /forms?search=clima&status=PENDENTE&page=0&size=20`

`search` procura texto literal no título e na descrição, sem diferenciar maiúsculas.
`status` aceita `RASCUNHO`, `PENDENTE` ou `CONCLUIDO`. Ambos são opcionais.
A página começa em zero, `size` aceita 1 a 100 e a busca até 240 caracteres.
A ordenação é por criação decrescente e, em caso de empate, identificador decrescente.

`data` contém `items`, `page`, `size`, `totalElements` e `totalPages` (zero sem resultados).
Cada item contém `id`, `title`, `description`, `questionCount` e `status`.
Não são carregadas respostas na listagem.

O gestor do workspace consulta todos os formulários do seu workspace. Outros gestores
consultam os que enviaram e os publicados dos quais são destinatários. Rascunhos de
outros remetentes ficam ocultos ao destinatário.

## Consulta de detalhes

`GET /forms/{id}` retorna todos os dados e perguntas, com `managerId`,
`senderManagerId`, `workspaceId`, `title`, `description`, `nrCode`, `unitId`,
`dueDate`, `status`, `createdAt`, `updatedAt`, `respondedAt` e `questions`.
O ID do formulário é uma string UUID de 36 caracteres, como `550e8400-e29b-41d4-a716-446655440000`.
Identificador inválido retorna 400; inexistente ou fora do escopo retorna 404.
O prazo é apresentado como `YYYY-MM-DD`, usando `America/Sao_Paulo`; no MongoDB
é armazenado como data BSON. As opções têm `id`, `text` e `other`.
As perguntas de foto têm `maxFiles` e `maxFileSizeMb`.
Os detalhes também incluem `unitLocation: {"latitude": -23.5, "longitude": -46.6}`,
obtido da unidade vinculada no mesmo workspace. Sem unidade ou coordenadas válidas,
`unitLocation` é `null`. O front usa esses dados para verificar a presença;
o envio das respostas não bloqueia por distância. Consulte [respostas e análise](forms-responses.md).

## Criação e rascunhos

`POST /forms` retorna 201, `Location: /forms/{id}` e os detalhes do documento criado.

Exemplo de corpo (não é o documento completo do MongoDB):

```json
{
  "title": "Inspeção de segurança",
  "description": "Condições da unidade",
  "managerId": 20,
  "nrCode": 6,
  "unitId": 3,
  "dueDate": "2026-10-30",
  "status": "PENDENTE",
  "questions": [
    {"type": "texto", "question": "Descreva o local", "required": true},
    {
      "type": "alternativa", "question": "Quais riscos foram encontrados?",
      "required": true, "multiple": true,
      "options": [{"text": "Queda", "other": false}, {"text": "", "other": true}]
    },
    {
      "type": "foto", "question": "Fotos do local", "required": false,
      "maxFiles": 3, "maxFileSizeMb": 10
    },
    {"type": "data", "question": "Data da inspeção", "required": true}
  ]
}
```

`status` omitido cria `PENDENTE`. Para um rascunho vazio, envie
`{"status":"RASCUNHO"}`. Não é permitido criar como `CONCLUIDO`.
Gestor e título podem faltar no rascunho; se gestor, unidade ou NR forem informados,
os vínculos precisam existir e estar elegíveis. A unidade opcional não define quem responde: somente `managerId` é o destinatário.
Quando definida, suas coordenadas são retornadas nos detalhes para comparação no front;
o envio não aplica validação de distância, conforme [forms-responses.md](forms-responses.md).

Na publicação são obrigatórios título não vazio, gestor ativo e 1 a 100 perguntas
com enunciados preenchidos. Descrição, NR, unidade e prazo são opcionais.
Limites: título 100, descrição 240, enunciado 140 e texto de opção 80 caracteres.
Cada alternativa aceita 2 a 100 opções, com no máximo uma marcada `other`.
O texto de `other` é normalizado para `Outros`; seu campo de resposta pertence ao mobile.
Fotos aceitam 1 a 10 arquivos e limites de 5, 10, 20, 50 ou 100 MB por arquivo.
O padrão de foto é 1 arquivo de até 10 MB. Não há upload de fotos nessas rotas.
Esses limites também se aplicam a rascunhos, que podem ter opções e enunciados vazios.

O `_id` dos formulários é um UUID versão 4 gerado por `UUID.randomUUID()` no Java,
salvo como string no MongoDB e exposto como `id` na API, inclusive na Home mobile.
Não há contador nem collection auxiliar. Perguntas e opções recebem IDs numéricos locais ao seu
formulário/pergunta. Omita IDs em itens novos. As respostas são inicializadas como
arrays vazios. Campos de remetente, workspace, resposta e datas enviados pelo cliente
são ignorados; a API usa exclusivamente seus próprios valores para esses campos.
Datas de criação e atualização usam precisão de milissegundos, compatível com BSON,
para que a criação e as consultas seguintes retornem os mesmos valores persistidos.

Os validadores são Strategies por tipo, selecionadas por enum, com modos `DRAFT`
e `PUBLISH`. Regras gerais de identidade, vínculos, estado e persistência ficam nos
serviços do módulo. Não há transação distribuída entre as consultas PostgreSQL e
a gravação MongoDB; os vínculos são conferidos a cada operação de escrita.

## Edição

`PUT /forms/{id}` substitui os campos editáveis e toda a lista de perguntas, retornando
200 com os detalhes. Campos opcionais omitidos são limpos. O status é preservado;
se enviado, deve ser igual ao atual. Um rascunho só passa a pendente por publicação.

Gestores comuns só alteram formulários dos quais são remetentes. O gestor do workspace
pode alterar os de outros remetentes no próprio workspace. O destinatário, por ser
destinatário, não ganha permissão administrativa. A falta dessa permissão retorna 403.
Formulários concluídos ou que já tenham respostas são protegidos contra edição (409).

Preserve o `id` de cada pergunta e opção existente. Omita os IDs nos novos itens
e nas cópias; remova itens omitindo-os do array e reordene mudando sua posição.
IDs desconhecidos ou repetidos são recusados. Remetente, workspace, criação e respostas
não podem ser sobrescritos por essa operação.

A gravação verifica atomicamente escopo, remetente quando necessário, status, ausência
de respostas e versão interna do documento. Alterações concorrentes detectadas retornam
409; consulte novamente antes de repetir. A versão interna protege o intervalo entre
a leitura do serviço e a escrita, sem representar um controle de versão da tela do usuário.

## Exclusão

`DELETE /forms/{id}` retorna 204, sem corpo. Somente o remetente ou o gestor do
workspace pode excluir, e apenas enquanto não houver conclusão nem respostas.
O gestor do workspace também respeita esse bloqueio. Não é uma exclusão lógica.
A operação verifica a mesma versão e o estado do documento de forma atômica:
conclusão ou alteração concorrente impede a remoção e retorna 409.
Formulário inexistente ou fora do escopo retorna 404.

## Publicação

`POST /forms/{id}/publish` não exige corpo. Revalida os dados salvos e os vínculos
atuais no PostgreSQL; depois altera apenas `RASCUNHO` para `PENDENTE`, a atualização
e a versão interna. Retorna 200 com os detalhes e mantém perguntas e respostas.
Somente o remetente ou o gestor do workspace pode publicar. Rascunho incompleto
retorna 400; publicação repetida, formulário concluído ou corrida de atualização
retornam 409. O destinatário passa a enxergar o formulário após a publicação.

## Persistência e compatibilidade

A collection é `forms`, com um documento por formulário e perguntas embutidas.
Os documentos novos usam `_id` do tipo string UUID. IDs numéricos ou ObjectIds antigos exigem
migração explícita, incluindo referências externas; a API não os converte automaticamente.
O campo `id_workspace` é obrigatório para acesso pelas rotas web. Documentos antigos
sem esse vínculo não ficam acessíveis até uma migração explícita e conferida.
A Home mobile passa a consultar somente documentos com `status: PENDENTE`.
Documentos antigos precisam ter seu status definido antes de aparecer nessa consulta.
Não inferimos conclusão a partir de perguntas opcionais sem resposta.
Opções antigas como arrays de strings precisam ser migradas para objetos com
`id_opcao`, `texto` e `outra` antes de usar a consulta completa/edição web.

Índices sugeridos para provisionamento no banco (a API não altera índices ao iniciar):

```javascript
db.forms.createIndex({id_workspace: 1, criado_em: -1, _id: -1})
db.forms.createIndex({id_workspace: 1, id_gestor_remetente: 1, criado_em: -1, _id: -1})
db.forms.createIndex({id_workspace: 1, id_gestor: 1, status: 1, criado_em: -1, _id: -1})
db.forms.createIndex({id_gestor: 1, status: 1, data_limite: 1, _id: 1})
```

## Organização do módulo

`FormRepository` é o único repository de formulários: combina `MongoRepository` com
o fragmento `FormRepositoryCustom`, implementado por `FormRepositoryCustomImpl` para
consultas paginadas e gravações condicionais no MongoDB. O fragmento retorna documentos
e resultados do banco, sem montar DTOs ou traduzir regras de negócio. Os vínculos
relacionais reutilizam `UserRepository`, `UnitRepository` e `NrCatalogRepository`.

Os controllers cuidam do HTTP. Os services coordenam autorização, validações,
identificadores e estados. `FormMapper` concentra conversões e a resposta paginada.
Os DTOs de request referenciam mensagens em português de `ValidationMessages.properties`.

`FormAccessService` decide a política de visibilidade por perfil e produz `FormReadFilter`.
Também produz `FormWriteCondition`, um snapshot imutável com ID, workspace, remetente
quando exigido, versão e status esperados. O service captura essa condição antes de
alterar o conteúdo. `FormService` define a transição de publicação para `PENDING`
e normaliza a busca antes de consultar o repository.

O repository traduz os filtros recebidos para MongoDB e executa `updateStatus` com
o estado de destino informado pelo service. Não consulta perfis nem escolhe estados
de publicação/visibilidade. Permanece responsável por ordenação, projeção, paginação,
incremento da versão e gravações condicionais. Workspace, remetente quando exigido,
versão, status anterior e ausência de respostas são conferidos atomicamente na
gravação/exclusão, protegendo inclusive respostas recebidas após a leitura do service.

Os identificadores Java usam inglês, incluindo `nrCode`, `DRAFT`, `PENDING`,
`COMPLETED`, `TEXT`, `OPTION`, `PHOTO` e `DATE`. Os DTOs usam diretamente os
nomes Java no JSON, incluindo `nrCode`, `managerId`, `questions`, `required` e `answer`.
Os campos físicos do MongoDB continuam mapeados por `@Field`, incluindo `nr`,
`id_gestor`, `perguntas` e `resposta`; a remoção de `@JsonProperty` não exige migração
dos documentos. Os valores de enum permanecem `RASCUNHO`, `PENDENTE`, `CONCLUIDO`,
`texto`, `alternativa`, `foto` e `data`. A Home mobile mantém o contrato próprio
existente; esta mudança se aplica aos bodies e respostas dos endpoints `/forms`.

## Validação local

`./mvnw test` executa testes de serviço, Strategies, contratos HTTP e segurança Firebase.
Para executar também `FormRepositoryMongoTest`, aponte `FORM_TEST_MONGODB_URI` para
uma instância MongoDB descartável em `mongodb://127.0.0.1:<porta>` ou
`mongodb://localhost:<porta>`. Os testes criam e removem apenas uma base própria
`forms_test_<uuid>`; não carregam a conexão da aplicação nem usam seu `.env`.
O CI fornece uma instância MongoDB para esses testes.

A integração cobre persistência BSON, escopo de consultas, geração concorrente de UUIDs,
gravações condicionais e um fluxo HTTP completo de criação do rascunho até publicação.
A continuação do fluxo mobile, com envio de respostas/imagens e análise web, está em
[forms-responses.md](forms-responses.md).

## DTOs de criação e edição

- `FormRequest` recebe o formulário completo nos endpoints `POST /forms` e `PUT /forms/{id}`: dados gerais e lista de perguntas.
- `FormQuestionDefinitionRequest` define cada pergunta dessa lista: tipo, enunciado, obrigatoriedade, opções e limites de fotos.

Um formulário com cinco perguntas usa um `FormRequest` com cinco itens `FormQuestionDefinitionRequest` em `questions`. Os nomes distinguem as operações no formulário da definição de suas perguntas.
