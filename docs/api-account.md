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

## GET /user/me/account-details (SCRUM-570)

HTTP 200, mesmo envelope, com exatamente estes nove campos em `data`:

```json
{
  "nome": "Maria Rosa",
  "cpf": null,
  "email": "maria@example.com",
  "roleId": 20,
  "cargo": "Engenheira",
  "unitId": 10,
  "unidade": "Osasco",
  "modalidade": "PRESENCIAL",
  "profilePhotoUrl": null
}
```

`modalidade` usa `HIBRIDO`, `REMOTO` ou `PRESENCIAL`, como no mobile; pode ser
`null` em cadastros antigos. Nome, CPF, cargo/roleId e foto também podem ser
`null` quando ausentes. IDs e nomes vêm dos vínculos reais. Vínculos atuais
inativos continuam visíveis; cargo de outro workspace bloqueia o acesso.
Não retorna senha, NRs, tokens, status, workspaceId nem a entidade completa.

O CPF já é armazenado em `usuario.cpf` nos colaboradores importados e preservado
na promoção a GESTOR. O cadastro de GESTOR_WORKSPACE em
`WorkspaceRegistrationTransactionalService` não persiste o CPF nem a modalidade
da linha do gestor. Esta entrega consulta o valor real: não reconstrói dados
da planilha, não faz preenchimento retroativo e não inventa valores. A correção
do cadastro/importação é uma pendência separada; a edição da conta permite
informar os dados reais ausentes.

## Erros

A configuração de segurança existente responde **403** para token ausente ou
inválido (antes de entrar no controller, sem garantia do envelope ApiResult).
Perfil não permitido ou conta inativa também não obtêm acesso. Erros tratados
pelo controller advice usam `ApiResult` com `success: false`, `data: null`,
`message`, `errors` e `path`: 403 para acesso negado, 404 para usuário não
encontrado, 500 para falha interna/foto e 503 para indisponibilidade dos dados.
O filtro pode rejeitar um UID sem conta antes de alcançar o 404 do serviço.
