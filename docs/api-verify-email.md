# Verificação de e-mail

`POST /verify-email` recebe um corpo JSON com `email` e identifica o perfil cadastrado.

```json
{"email":"admin@astro.com"}
```

A consulta prioriza a tabela `admin`. Se o e-mail existir nela, a resposta é `200`
com `data.userType` igual a `ADMIN` e `data.userStatus` explicitamente `null`:

```json
{
  "userType": "ADMIN",
  "userStatus": null
}
```

O exemplo acima representa o objeto `data` do envelope padrão `ApiResult`.
Administradores são contas técnicas independentes dos workspaces e não possuem
coluna de status. O retorno `null` não significa conta desativada.
Se o mesmo e-mail estiver em `admin` e `usuario`, prevalece `ADMIN`.

Para contas em `usuario`, os valores existentes são preservados:

| Campo | Valores |
| --- | --- |
| `userType` | `GESTOR`, `GESTOR_WORKSPACE`, `COLABORADOR` |
| `userStatus` | Status cadastrado: `PRE_CADASTRADO`, `ATIVO` ou `DESATIVADO` |

E-mail ausente nas duas tabelas retorna `404`. Corpo inválido ou e-mail inválido
retorna `400`. A consulta mantém a comparação exata do e-mail do contrato anterior.

A identificação pelo e-mail não autentica o usuário nem concede permissões.
A autorização continua validando o token Firebase e o nível de acesso no banco.

Referências do banco:

- [Tabelas `conta`, `usuario` e `admin`](https://github.com/Astro-Inter/banco-de-dados/blob/main/database/scripts/1_create_table.sql).
- [Prioridade de ADMIN na função de autorização](https://github.com/Astro-Inter/banco-de-dados/blob/main/database/functions/03_fn_retornar_nivel_acesso.sql).
- [Documentação do banco](https://astro-inter.github.io/banco-de-dados/).
