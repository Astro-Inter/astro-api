# API de Chat

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

O campo `data` em `mensagens` é BSON Date e `_id`/`id_conversa` são strings UUID,
conforme a representação usada pelo módulo chat. O cursor URL-safe codifica a
versão, conversa, data em epoch milliseconds e UUID da mensagem. O cursor é
validado e vinculado à conversa, mas não concede autorização.

MongoDB filtra `id_conversa` e, para páginas seguintes, aplica a comparação
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

### Índice MongoDB

A consulta depende do índice composto abaixo para manter o filtro, a ordenação e
a continuação do cursor eficientes em históricos grandes:

```javascript
db.mensagens.createIndex({ id_conversa: 1, data: -1, _id: -1 })
```

Antes de provisioná-lo, confira os índices reais da collection `mensagens` e
reutilize um índice com as mesmas chaves e ordem. O repositório da aplicação não
declara índices para essa collection e não cria índices automaticamente.
