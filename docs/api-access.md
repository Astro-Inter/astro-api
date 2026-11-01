# Pré-cadastro e acesso inicial

As rotas deste guia são públicas e não exigem token bearer. Elas fazem parte do
fluxo de primeiro acesso; o login com senha ocorre no Firebase do cliente.
Consulte também [verificação de e-mail](api-verify-email.md) e
[cadastro de workspace](api-workspace-registration.md).

## Enfileirar e-mail do gestor

`POST /add-manager-email` recebe JSON com `email` válido e não vazio:

```json
{"email":"gestor@example.com"}
```

A API solicita a inclusão do e-mail na lista Redis do pré-cadastro de workspace
e retorna `204 No Content` se a chamada não lançar exceção. O serviço não valida
o valor retornado pelo Redis; o `204` não confirma o processamento da fila nem a
entrega de e-mail. Um corpo inválido retorna `400`.

## Verificar chave de acesso

`POST /verify-key?tipoConta=COLABORADOR` recebe `email` e `accessKey`, uma string
de exatamente seis dígitos:

```json
{"email":"pessoa@example.com","accessKey":"012345"}
```

`tipoConta` é opcional e assume `COLABORADOR` quando omitido ou vazio.
Para esse tipo, a consulta exige colaborador `PRE_CADASTRADO` e compara a chave
com a armazenada no Redis. `GESTOR_WORKSPACE` consulta a chave de workspace no
Redis sem exigir usuário já cadastrado. O e-mail é normalizado para a consulta.
O parâmetro não atribui perfil nem autentica a pessoa; a comparação não consome
a chave.

Chave válida retorna `204` sem body. Chave incorreta ou ausente, ou colaborador
inelegível, retorna `403` sem body. `GESTOR` retorna `400` sem body; valor de
tipo não reconhecido ou dados inválidos também resultam em `400` pelo tratamento
de conversão ou validação.

## Ativar usuário

`POST /activate` recebe JSON com `email` válido e `firebaseUid` não vazio:

```json
{"email":"pessoa@example.com","firebaseUid":"uid-do-firebase"}
```

O serviço localiza e bloqueia o usuário pelo e-mail em uma transação. Somente
quando o UID atual é nulo, vazio ou contém apenas espaços, grava o UID informado e retorna `204`
sem body. A atualização de status depende da trigger do banco. Usuário não
encontrado retorna `404`; dados inválidos retornam `400`. Conta com UID já
preenchido retorna `409` com `ApiResult`, inclusive se o UID enviado for o mesmo.
O bloqueio impede que duas ativações simultâneas substituam o vínculo. A implementação desta
rota não recebe nem verifica a chave no mesmo pedido: a verificação descrita
acima é uma chamada separada.

## Verificação

Os testes existentes incluem `AuthControllerTest`, `UserServiceTest` e
`PreRegistrationQueueServiceTest`. O comportamento da trigger e o processamento
da fila dependem dos serviços externos configurados.
