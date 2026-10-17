# Conta da pessoa autenticada (SCRUM-568)

As rotas usam o token Firebase em `Authorization: Bearer <token>` e atendem somente
GESTOR e GESTOR_WORKSPACE ativos. A identidade é o UID validado pelo filtro da API;
não há parâmetro de usuário. O tipo e o status atuais são conferidos no banco.
A confirmação de senha no navegador não concede autorização no servidor.

## GET /user/me/account-summary (SCRUM-569)

Retorna HTTP 200 com os únicos campos de dados abaixo:

```json
{
  "success": true,
  "message": "Resumo da conta retornado com sucesso",
  "data": { "nome": "Maria Rosa", "email": "maria@example.com", "profilePhotoUrl": null },
  "errors": null,
  "path": "/user/me/account-summary"
}
```

A foto usa o mesmo serviço de URL temporária do mobile; sem foto retorna `null`.
Nome ausente também permanece `null`. Não são enviados CPF, NRs, IDs, tokens ou
outros dados internos. `GET /user/me` e `PUT /user/me/profile-photo` permanecem
com seus contratos existentes.

## Erros

A configuração de segurança existente responde **403** para token ausente ou
inválido (antes de entrar no controller, sem garantia do envelope ApiResult).
Perfil não permitido ou conta inativa também não obtêm acesso. Erros tratados
pelo controller advice usam `ApiResult` com `success: false`, `data: null`,
`message`, `errors` e `path`: 403 para acesso negado, 404 para usuário não
encontrado, 500 para falha interna/foto e 503 para indisponibilidade dos dados.
O filtro pode rejeitar um UID sem conta antes de alcançar o 404 do serviço.
