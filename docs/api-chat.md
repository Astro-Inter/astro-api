# API de Chat

## Listar conversas e contatos

`GET /chats` e `GET /chats/contacts` exigem token Firebase e perfil
`COLABORADOR`, `GESTOR` ou `GESTOR_WORKSPACE`. O usuário e o workspace são
determinados pela identidade autenticada.

`GET /chats` retorna HTTP 200 com um **array diretamente**, sem envelope
`ApiResult`. Cada conversa tem `id`, `participant` (`id`, `name`, `role`,
`profilePhotoUrl`, `avatarInitials`), `lastMessage` (`senderId`, `content`) e
`unreadCount`. Inclui conversas válidas com dois participantes do mesmo
workspace e ao menos uma mensagem; a ordenação é pela última mensagem mais
recente e, em empate, pelo ID da conversa decrescente. O contador vem do Redis;
valor ausente é zero. Sem conversas, retorna `[]`.

`GET /chats/contacts` retorna `ApiResult` com array em `data`. Cada contato tem
`id`, `name` e `role`. A consulta exclui o próprio usuário e considera usuários
ativos em unidades ativas do mesmo workspace. Colaboradores podem consultar
gestores; gestores podem consultar colaboradores e gestores. A conta solicitante
também precisa estar ativa, em unidade ativa e com perfil coerente com o token.
Os contatos são ordenados por nome e, em empate, por ID. Usuário inexistente
retorna `404` e conta inelegível retorna `403`.

## WebSocket/STOMP para eventos privados

O endpoint WebSocket é `GET /ws`. O handshake HTTP é permitido sem autenticação
Firebase; essa permissão só possibilita o upgrade do transporte. A sessão STOMP
precisa autenticar no frame `CONNECT` antes de poder assinar destinos. O servidor
aceita origens de navegador listadas em `CORS_ALLOWED_ORIGINS` e não usa origem
curinga. Clientes Android nativos podem conectar sem o cabeçalho `Origin`.

Envie o Firebase ID Token no cabeçalho STOMP `Authorization` usando o formato
`Bearer <token>`. O token não deve ser incluído na URL. A API valida o token pelo
`FirebaseTokenService` existente, procura o UID no PostgreSQL e exige uma conta
`ATIVO`, com unidade ativa e vínculo com Workspace, nos tipos `COLABORADOR`,
`GESTOR` ou `GESTOR_WORKSPACE`. Administradores e contas sem vínculo elegível
são recusados. O principal da sessão contém a identidade confiável da conexão;
`Principal.getName()` retorna o ID numérico do colaborador e o contexto também
mantém o ID do Workspace e o tipo de usuário.

Exemplo de conexão STOMP 1.2:

```text
CONNECT
Authorization:Bearer FIREBASE_ID_TOKEN
accept-version:1.2
heart-beat:10000,10000

```

Exemplo de assinatura permitida:

```text
SUBSCRIBE
id:private-messages
destination:/user/queue/messages

```

Cada instância mantém um broker simples para as conexões WebSocket locais.
Eventos destinados a outras instâncias passam pelo canal Redis Pub/Sub
`astro:conversa:entrega:v1`; cada instância encaminha o evento somente às sessões
locais do usuário destinatário. Todas as instâncias precisam usar o mesmo Redis
e ter permissão de publicação e assinatura nesse canal. O Pub/Sub não armazena
eventos para clientes desconectados; nesses casos, o histórico REST permite
recuperar mensagens persistidas. O broker simples atende somente `/queue`;
`/app` recebe o handler autenticado
`/app/chat.send` e `/user` identifica a fila privada resolvida pelo Spring a
partir do principal da conexão. Assinaturas permitidas são
`/user/queue/messages` e `/user/queue/errors`. Assinaturas a `/queue/**`,
`/topic/**` e caminhos diretos de outros usuários continuam recusadas. O único
destino de publicação permitido é `/app/chat.send`.

O broker negocia heartbeats de 10 segundos e usa o agendador Spring para a
manutenção do broker simples. Uma autenticação bem-sucedida no `CONNECT` não
revalida periodicamente o Firebase ID Token; uma conexão longa permanece
autenticada até encerrar. O Android deve reconectar após desconexões e enviar
um token Firebase válido em cada novo `CONNECT`. Não há controle de presença.

## Enviar mensagem por STOMP

O destino é `/app/chat.send`. O JSON deve conter exatamente um entre
`recipientId` e `conversationId`, além de `content` e `clientMessageId`.
`recipientId` é o ID numérico interno PostgreSQL e só é usado na primeira
mensagem ou quando o Android ainda não recebeu o ID da conversa. Conversa só é
inserida junto com a primeira mensagem na transação MongoDB; abrir a tela,
listar contatos ou conectar não altera `conversas`.

```text
SEND
destination:/app/chat.send
content-type:application/json

{"recipientId":25,"content":"Olá!","replyToMessageId":null,"clientMessageId":"aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"}
```

Para mensagens seguintes, envie `conversationId` no lugar de `recipientId`.
`senderId`, timestamps e workspace não são aceitos do cliente. O conteúdo deve
ter de 1 a 4000 caracteres e não pode conter apenas espaços; quebras de linha e
espaços internos são preservados. UUIDs são exigidos para IDs de operação,
conversa e reply. Um reply precisa existir na mesma conversa.

O servidor resolve remetente pelo Principal STOMP e revalida no PostgreSQL o
estado ativo, tipo, unidade ativa e Workspace dos dois participantes em cada
envio. COLABORADOR só pode enviar a GESTOR ou GESTOR_WORKSPACE. GESTOR e
GESTOR_WORKSPACE podem enviar aos três tipos operacionais. ADMIN, usuários
desativados, comunicação entre Workspaces e envio para si mesmo são recusados.

A mensagem persistida e a conversa são gravadas em uma transação MongoDB. O
MongoDB configurado deve ser replica set ou cluster que suporte transações;
em standalone a operação falha sem confirmar envio. A aplicação lê
`MONGODB_URI` do ambiente e não consegue inferir pela configuração local se o
servidor remoto oferece esse recurso. A API não cria índices personalizados.
Para impedir duplicações concorrentes, gera o `_id` da conversa a partir do par
ordenado de participantes e o `_id` da mensagem a partir do remetente e do
`clientMessageId`. O MongoDB já mantém `_id` único em cada collection. Os IDs
resultantes continuam sendo UUIDs gerados pelo backend. Conversas antigas são
localizadas pelos participantes e reutilizadas. Se houver mais de uma conversa
antiga para o mesmo par, o envio é recusado para esse par; nenhum histórico é
removido automaticamente.

Reenvios precisam manter o mesmo `clientMessageId` e o mesmo conteúdo/reply.
Um UUID reutilizado para outra operação é rejeitado ao comparar a mensagem
persistida. A consulta de pares existentes não tem índice personalizado e
pode percorrer a collection `conversas`.

Após persistir, o backend executa um script Lua no Redis que consulta o ID da
mensagem em `conversa:nao_lidas:processadas:{conversationId}`, faz `INCR`
`conversa:nao_lidas:{conversationId}:{recipientId}` e registra o ID processado com
`SADD`, numa única execução atômica. O incremento é repetido
com o mesmo ID não conta de novo. Se o Redis estiver indisponível após a
gravação MongoDB, o servidor retorna erro correlacionado; o Android deve
reenviar a operação com o mesmo ID. O marcador idempotente Redis não tem TTL e
cresce por mensagem, pois sua expiração permitiria contagem duplicada em
reenvios tardios. MongoDB, Redis e entrega WebSocket não compartilham uma
transação distribuída. A contagem exata entre reinicializações depende da
política de persistência e eviction configurada para Redis; a aplicação não
define essas opções.

A validação de acesso à listagem, leitura e marcação como lida exige que a conta
atual esteja `ATIVO`, sua unidade esteja ativa e o papel no token corresponda ao
tipo atual cadastrado. Contas `PRE_CADASTRADO` e `DESATIVADO` não acessam as
conversas nem a lista de contatos.

Após a persistência e o incremento Redis, o evento é publicado pelo Redis Pub/Sub e
encaminhado às conexões locais do destinatário em `/user/queue/messages`, e a confirmação da própria mensagem é devolvida ao
remetente na mesma fila privada. Uma repetição pode reenviar o evento, portanto
o Android deve deduplicar pela propriedade `id`. A confirmação significa que a
mensagem foi persistida, contabilizada no Redis e publicada no canal Pub/Sub;
não confirma que o dispositivo destinatário recebeu o frame.

Resposta/entrega de sucesso:

```json
{
  "clientMessageId": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
  "id": "22222222-3333-4444-8555-666666666666",
  "conversationId": "11111111-2222-4333-8444-555555555555",
  "senderId": 1,
  "content": "Olá!",
  "replyToMessageId": null,
  "createdAt": "2026-10-26T18:30:00Z"
}
```

Erros de negócio e infraestrutura são enviados apenas ao remetente em
`/user/queue/errors`, com `clientMessageId`, código e mensagem segura. Em
`UNREAD_COUNTER_UNAVAILABLE`, `DELIVERY_UNAVAILABLE`,
`PERSISTENCE_UNAVAILABLE` ou `SEND_FAILED`, repita com o mesmo ID; não gere uma
nova operação se a confirmação não chegou.

```json
{"clientMessageId":"aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee","code":"FORBIDDEN","message":"Comunicação entre estes perfis não é permitida"}
```

## Consultar histórico de mensagens

`GET /chats/{conversationId}/messages?limit=30&before={nextCursor}` requer um
Bearer token Firebase de colaborador, gestor ou gestor do workspace.

`conversationId` é o UUID string do `_id` em `conversas`. `limit` é opcional,
com padrão 30 e intervalo de 1 a 100. `before` é o cursor opaco devolvido pela
página anterior. UUID, limite ou cursor inválido retornam 400 no envelope
`ApiResult`; conversa inexistente retorna 404, usuário sem participação ou
conversa fora do workspace retorna 403.

A identidade vem do principal Firebase. A API resolve o usuário e seu workspace
no PostgreSQL, lê a conversa em `conversas`, exige que o usuário autenticado
seja um dos dois participantes e confirma que ambos os IDs pertencem ao mesmo
workspace atual. A consulta do outro participante não exige status ativo nem
tipo específico, preservando o acesso ao histórico quando esses atributos
mudarem. O esquema atual não armazena o workspace no documento de conversa, por
isso o escopo é inferido dos participantes relacionados no PostgreSQL.

O campo `data` em `mensagens` é BSON Date e `_id`/`conversa_id` são strings UUID,
conforme a representação usada pelo módulo chat. O cursor URL-safe codifica a
versão, conversa, data em epoch milliseconds e UUID da mensagem. O cursor é
validado e vinculado à conversa, mas não concede autorização.

MongoDB filtra `conversa_id` e, para páginas seguintes, aplica a comparação
estrita `(data < cursorDate) OR (data = cursorDate AND _id < cursorMessageId)`.
Ordena por `data DESC, _id DESC`, limita em `limit + 1`, e projeta somente os
campos da mensagem. A API remove o item excedente, cria `nextCursor` com a
mensagem mais antiga da página e inverte os resultados para ordem cronológica.
O GET não acessa Redis nem altera documentos.

Resposta de sucesso (dentro de `data` do envelope `ApiResult`):

```json
{
  "messages": [
    {
      "id": "22222222-3333-4444-8555-666666666666",
      "conversationId": "11111111-2222-4333-8444-555555555555",
      "senderId": 1,
      "content": "Sim, já concluí!",
      "replyToMessageId": "33333333-4444-4555-8666-777777777777",
      "createdAt": "2026-10-21T10:05:00Z"
    }
  ],
  "nextCursor": "CURSOR_OPACO_RETORNADO_PELA_API",
  "hasMore": true
}
```

Para conversas sem mensagens, `messages` é vazio, `nextCursor` é null e
`hasMore` é false. Na última página, `nextCursor` também é null.

## Marcar conversa como lida

`PATCH /chats/{conversationId}/read` requer um Bearer token Firebase de
colaborador, gestor ou gestor do workspace. O endpoint não recebe corpo,
query parameters, ID de usuário ou Workspace e retorna `204 No Content` sem
body.

O `conversationId` deve ser um UUID no formato canônico usado em `_id` na
collection `conversas`. A API resolve o colaborador pelo UID do principal
Firebase, verifica a conversa e seus dois participantes no MongoDB e confirma
no PostgreSQL que o usuário autenticado participa dela e que ambos os
participantes pertencem ao Workspace atual. Conversa inexistente retorna 404,
ID inválido retorna 400 e falta de participação ou divergência de Workspace
retorna 403. `ADMIN` não tem acesso ao Chat operacional. O status do outro
participante não é consultado como critério de autorização, então sua
desativação não bloqueia a leitura do histórico existente.

Após autorizar, a API executa uma única operação Redis `DEL` na chave
`conversa:nao_lidas:{conversationId}:{userId}`, usando o ID PostgreSQL do usuário
autenticado. Por exemplo, para a conversa
`11111111-2222-4333-8444-555555555555` e usuário `25`, a chave removida é
`conversa:nao_lidas:11111111-2222-4333-8444-555555555555:25`. A chave do outro
participante não é alterada. A remoção é idempotente, inclusive se a chave já
não existir; erros de conexão Redis são propagados pelo tratamento central de
infraestrutura.

Exemplo:

```http
PATCH /chats/11111111-2222-4333-8444-555555555555/read
Authorization: Bearer FIREBASE_ID_TOKEN
```

Resposta de sucesso: `204 No Content`, sem conteúdo.

O contador simples tem uma condição de corrida conhecida: se uma nova mensagem
incrementar a chave entre a solicitação de leitura e o `DEL`, essa mensagem
pode deixar de ser contabilizada como não lida. O modelo atual não registra
leituras por mensagem; esta operação não oferece garantia absoluta de leitura
nesse cenário concorrente.

O histórico e a busca de conversas por participantes não dependem de índices
personalizados criados pela API. Sem índices apropriados já existentes no
MongoDB, essas consultas podem ficar mais lentas conforme as collections
crescem.


## Indicador temporário de digitação (SCRUM-261)

A conexão STOMP autenticada existente anuncia `astro-typing:1` no frame
`CONNECTED`, preservando versão, identidade e heartbeats. Clientes novos só
ativam o recurso quando recebem esse cabeçalho; clientes antigos continuam
usando mensagens normalmente. Não foi adicionado endpoint HTTP.

- Publicar em `/app/chat.typing`.
- Inscrever em `/user/queue/typing` após conectar e novamente após reconectar.
- Entrada `ChatTypingRequest`: `typing` obrigatório (boolean) e exatamente um
  seletor: `recipientId` (Long) ou `conversationId` (UUID). Campos desconhecidos,
  inclusive identidade do remetente e texto digitado, são rejeitados.
- Saída `ChatTypingResponse`: `conversationId`, `senderId`, `typing`, `expiresAt`.
  A identidade vem do principal autenticado e o prazo é calculado no servidor:
  cinco segundos para início/renovação, instante atual para encerramento.
- A autorização revalida participantes, identidade Firebase, perfil, status e
  unidade ativos, workspace e política de comunicação do envio de mensagens.
  Uma conversa informada precisa existir e incluir o remetente. Para um contato
  autorizado, consulta a conversa existente pelo par antes de calcular um ID
  pela regra determinística. Nenhum documento é criado.
- O canal Redis `astro:conversa:digitacao:v1` distribui os eventos entre instâncias,
  entregando somente na fila privada do destinatário. Eventos de início vencidos
  são descartados. Não há replay offline, outbox, contador de não lidas ou push.
- Redis limita renovações de início a uma por par em 1,5 segundo; parar remove
  essa chave temporária e publica o encerramento. O cliente limita a dois segundos.
- Erros usam `/user/queue/errors`, com `clientMessageId:null` e códigos
  `TYPING_FORBIDDEN`, `TYPING_INVALID_REQUEST` ou `TYPING_UNAVAILABLE`.
  São independentes da confirmação de envio de mensagens.

O mobile exibe somente três pontinhos animados no final da conversa, em um
item temporário separado do histórico. Apagar o texto, enviar, sair da conversa,
ficar em segundo plano ou desconectar limpa o sinal. Um destinatário limpa o
indicador ao receber a mensagem ou expirar o prazo, mesmo sem evento de parada.
A animação respeita a configuração de animações do Android.

Para validar: publicar esta API; conectar dois usuários autorizados do mesmo
workspace, abrir a conversa nos dois clientes e digitar em um deles. Conferir
os pontinhos no outro, renovação durante a digitação e desaparecimento ao parar,
enviar, sair da tela ou desligar a rede. Usuários sem permissão e outros
workspaces não devem receber eventos. API antiga deve manter o chat normal,
com o indicador desativado por ausência do cabeçalho de capacidade.

## Responsabilidades da persistência Mongo

ChatMongoRepository encapsula consultas, projeções, cursores e gravações. ChatMongoMapper converte documentos BSON e modelos do chat, preservando resultados ausentes. A consulta por par retorna até duas conversas; ChatService valida a unicidade e responde com conflito quando há mais de uma. A busca por mensagem mantém o filtro de conversa e a sessão transacional no repository.
