# Swagger e OpenAPI

Com a API em execução, abra [Swagger UI](http://localhost:8080/swagger-ui/index.html).
O contrato JSON está em [OpenAPI](http://localhost:8080/v3/api-docs).
Use a porta configurada em seu ambiente quando ela for diferente de 8080.

## Autenticação

1. Faça login no cliente usando Firebase Authentication.
2. Obtenha o ID token pelo SDK Firebase do cliente.
3. No Swagger, clique em **Authorize** e informe apenas o token, sem `Bearer`.
4. Abra uma operação, preencha os parâmetros e use **Try it out** e **Execute**.

O Swagger envia `Authorization: Bearer <token>`. Quando o token expirar, obtenha
outro no cliente e atualize a autorização. A API verifica o perfil e o acesso ao
workspace e aos registros solicitados. O Swagger executa chamadas reais: use
dados de teste ao executar operações que modificam dados.

As rotas públicas são `POST /verify-email`, `/activate`, `/verify-key`,
`/add-manager-email` e `/register-workspace`. Elas não exigem bearer token.
O login com senha ocorre no Firebase do cliente.

## Organização no código

Cada controller implementa uma interface `<Nome>Swagger` no pacote `swagger` do
mesmo domínio, seguindo a organização adotada pelo Delta. Essas interfaces
concentram `@Tag`, `@Operation`, `@ApiResponse` e a documentação de parâmetros.
As anotações de rotas Spring MVC, validação e autorização permanecem no controller.
Os DTOs continuam definindo os schemas dos corpos e respostas.

A segurança de métodos usa `proxyTargetClass = true` para manter os controllers
como proxies de classe mesmo após a introdução das interfaces de documentação.
As regras `@PreAuthorize` continuam nos controllers.

`OpenApiConfig` apresenta a API, explica o uso do token Firebase e configura o
esquema `bearerAuth`. Os guias Markdown complementam o contrato com fluxos e
regras que precisam de mais contexto.

Para primeiro acesso, consulte [pré-cadastro e acesso](api-access.md); para dados
pessoais, [perfil](api-user-profile.md) e [conta](api-account.md). As consultas
das telas iniciais estão em [Home do workspace](home-workspace.md) e
[Home do colaborador e do gestor](home-pessoas.md). Consulte também
[notificações](api-notifications.md) e [chat](api-chat.md).

Ao adicionar ou alterar uma rota:

- Atualize a assinatura da interface junto com o controller.
- Use resumo em português e descreva as regras e permissões específicas.
- Documente os códigos HTTP que a implementação realmente retorna.
- Preserve exemplos, formatos, partes multipart e schemas de `ApiResult<T>`.
- Em rotas públicas, declare a ausência de requisitos de segurança no OpenAPI.

## Verificação

Execute `./mvnw.cmd -Dtest=SwaggerDocumentationTest,EventOpenApiTest test` no Windows
ou `./mvnw -Dtest=SwaggerDocumentationTest,EventOpenApiTest test` no Linux/macOS.
Os testes geram o documento a partir dos controllers reais, com dependências
simuladas, sem acessar PostgreSQL, MongoDB, Redis ou Firebase.
O contrato completo gerado fica em `target/astro-openapi.json`.
