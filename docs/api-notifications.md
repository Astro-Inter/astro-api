# Notificações

As rotas usam `Authorization: Bearer <Firebase ID token>` e aceitam os perfis
`COLABORADOR`, `GESTOR` e `GESTOR_WORKSPACE`. A identidade vem do token; a API
consulta apenas notificações pertencentes ao usuário autenticado. Respostas de
sucesso usam `ApiResult`.

## Listagem

`GET /notifications` retorna em `data` todas as notificações do usuário,
ordenadas por data de criação decrescente e, em empate, ID decrescente. Cada
item contém `id` (string), `title`, `viewed` e `hours` (horas inteiras desde a
criação, com mínimo zero). Se o título armazenado for nulo, a listagem usa a
mensagem como título. Uma lista vazia retorna `200` com `data: []`.

```json
{"success":true,"message":"Notificações retornadas com sucesso","data":[{"id":"64a000000000000000000001","title":"Novo evento","viewed":false,"hours":2}],"errors":null,"path":"/notifications"}
```

## Detalhes e visualização

`GET /notifications/{notificationId}` retorna `message`, `title` e `eventId`;
os dois últimos podem ser nulos. Consultar a lista ou os detalhes não marca a
notificação como visualizada.

`PATCH /notifications/{notificationId}/viewed` não exige body. Após conferir a
propriedade, registra a visualização e retorna `200` com `data: null`. Repetir a
operação é idempotente. O estado é individual e também aparece na Home do
colaborador. IDs são recebidos como strings; a busca contempla IDs MongoDB,
numéricos e textuais conforme o valor informado.

Usuário inexistente ou notificação inexistente ou de outra pessoa retorna `404`;
acesso sem permissão retorna `403`. Falhas de acesso aos dados podem retornar
`503`. A consulta das notificações usa MongoDB; a identidade vem do PostgreSQL.

## Verificação

Os testes existentes incluem `NotificationControllerTest` e
`NotificationServiceTest`. Eles verificam os contratos e regras com dependências
simuladas; não substituem uma verificação com os serviços configurados.
