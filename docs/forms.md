# Formulários da web

As rotas usam `Authorization: Bearer <Firebase ID token>` e o envelope `ApiResult`.
Somente gestores ativos, com unidade ativa e workspace válido, têm acesso.
O workspace e o remetente são determinados pela identidade validada.

## Listagem

`GET /forms?search=clima&status=PENDENTE&page=0&size=20`

`search` procura texto literal no título e na descrição, sem diferenciar maiúsculas.
`status` aceita `RASCUNHO`, `PENDENTE` ou `CONCLUIDO`. Ambos são opcionais.
A página começa em zero, `size` aceita 1 a 100 e a busca até 240 caracteres.
A ordenação é por criação decrescente e, em caso de empate, identificador decrescente.

`data` contém `items`, `page`, `size`, `totalElements` e `totalPages` (zero sem resultados).
Cada item contém `id`, `titulo`, `descricao`, `quantidade_perguntas` e `status`.
Não são carregadas respostas na listagem.

O gestor do workspace consulta todos os formulários do seu workspace. Outros gestores
consultam os que enviaram e os publicados dos quais são destinatários. Rascunhos de
outros remetentes ficam ocultos ao destinatário.

## Consulta de detalhes

`GET /forms/{id}` retorna todos os dados e perguntas, com `id_gestor`,
`id_gestor_remetente`, `id_workspace`, `titulo`, `descricao`, `nr`, `id_unidade`,
`data_limite`, `status`, `criado_em`, `atualizado_em`, `respondido_em` e `perguntas`.
Identificador inválido retorna 400; inexistente ou fora do escopo retorna 404.
O prazo é apresentado como `YYYY-MM-DD`, usando `America/Sao_Paulo`; no MongoDB
é armazenado como data BSON. As opções têm `id_opcao`, `texto` e `outra`.
As perguntas de foto têm `maximo_arquivos` e `tamanho_maximo_mb`.

## Criação e rascunhos

`POST /forms` retorna 201, `Location: /forms/{id}` e os detalhes do documento criado.

Exemplo de corpo (não é o documento completo do MongoDB):

```json
{
  "titulo": "Inspeção de segurança",
  "descricao": "Condições da unidade",
  "id_gestor": 20,
  "nr": 6,
  "id_unidade": 3,
  "data_limite": "2026-10-30",
  "status": "PENDENTE",
  "perguntas": [
    {"tipo": "texto", "pergunta": "Descreva o local", "obrigatoria": true},
    {
      "tipo": "alternativa", "pergunta": "Quais riscos foram encontrados?",
      "obrigatoria": true, "multipla_escolha": true,
      "opcoes": [{"texto": "Queda", "outra": false}, {"texto": "", "outra": true}]
    },
    {
      "tipo": "foto", "pergunta": "Fotos do local", "obrigatoria": false,
      "maximo_arquivos": 3, "tamanho_maximo_mb": 10
    },
    {"tipo": "data", "pergunta": "Data da inspeção", "obrigatoria": true}
  ]
}
```

`status` omitido cria `PENDENTE`. Para um rascunho vazio, envie
`{"status":"RASCUNHO"}`. Não é permitido criar como `CONCLUIDO`.
Gestor e título podem faltar no rascunho; se gestor, unidade ou NR forem informados,
os vínculos precisam existir e estar elegíveis. A unidade opcional é um vínculo
contextual e não define quem responde: somente `id_gestor` é o destinatário.

Na publicação são obrigatórios título não vazio, gestor ativo e 1 a 100 perguntas
com enunciados preenchidos. Descrição, NR, unidade e prazo são opcionais.
Limites: título 100, descrição 240, enunciado 140 e texto de opção 80 caracteres.
Cada alternativa aceita 2 a 100 opções, com no máximo uma marcada `outra`.
O texto de `outra` é normalizado para `Outros`; seu campo de resposta pertence ao mobile.
Fotos aceitam 1 a 10 arquivos e limites de 5, 10, 20, 50 ou 100 MB por arquivo.
O padrão de foto é 1 arquivo de até 10 MB. Não há upload de fotos nessas rotas.
Esses limites também se aplicam a rascunhos, que podem ter opções e enunciados vazios.

IDs de formulários são gerados por contador atômico em `sequencias`, acima do maior
ID numérico já existente. Perguntas e opções recebem IDs numéricos locais ao seu
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

Preserve `id_pergunta` e `id_opcao` dos itens existentes. Omita os IDs nos novos itens
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

Os identificadores Java usam inglês, incluindo `nrCode`, `DRAFT`, `PENDING`,
`COMPLETED`, `TEXT`, `OPTION`, `PHOTO` e `DATE`. `@JsonProperty`, `@Field`, o
conversor de status do MongoDB e a conversão do parâmetro HTTP preservam os nomes
do contrato existente: `nr`, `RASCUNHO`, `PENDENTE`, `CONCLUIDO`, `texto`,
`alternativa`, `foto` e `data`. Essa adequação não exige migração adicional de dados.

## Validação local

`./mvnw test` executa testes de serviço, Strategies, contratos HTTP e segurança Firebase.
Para executar também `FormRepositoryMongoTest`, aponte `FORM_TEST_MONGODB_URI` para
uma instância MongoDB descartável em `mongodb://127.0.0.1:<porta>` ou
`mongodb://localhost:<porta>`. Os testes criam e removem apenas uma base própria
`forms_test_<uuid>`; não carregam a conexão da aplicação nem usam seu `.env`.
O CI fornece uma instância MongoDB para esses testes.

A integração cobre persistência BSON, escopo de consultas, IDs concorrentes,
gravações condicionais e um fluxo HTTP completo de criação do rascunho até publicação.
Os endpoints de resposta e upload do mobile continuam fora deste módulo de entrega.
