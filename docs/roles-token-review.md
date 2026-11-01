# Cargos: identidade do token — SCRUM-481

Revisão do diff contra origin/main conforme astro-api-development e astro-code-review.
Nenhum achado acionável em aberto no diff de cargos.

Escopo: sete endpoints /roles, DTOs, controller, service, segurança, chamadas SQL,
testes e OpenAPI. O UID do solicitante deixou de ser um parâmetro de entrada.
As consultas escritas existentes são necessárias para acionar as rotinas SQL que
validam gestor ativo e workspace; suas assinaturas não foram alteradas.
As transações permanecem no service e as respostas usam ApiResult. Não houve
alteração em entidades, campos JSON além da remoção do UID, efeitos externos ou regras de NRs.

## Busca nos demais módulos

A busca por firebaseUid/firebase_uid em DTOs de request, controllers e serviços
web não identificou outro parâmetro redundante de solicitante em rotas protegidas.
Eventos, notificações, perfil e home obtêm a identidade do principal. POST /activate
é um caso público comunicado separadamente ao solicitante; UID em dados de retorno ou persistência não é
uma entrada de identidade controlada pelo solicitante.

## Limites

Firebase é simulado nos testes de segurança. As procedures não foram alteradas;
a validação real de suas permissões no banco não foi repetida nesta tarefa.

## Verificações executadas

Com Java 21:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress '-DargLine=-Djdk.net.unixdomain.tmpdir=C:/codex-unavailable-uds' verify
```

Resultado: BUILD SUCCESS, **745 testes, zero falhas/erros e 63 ignorados** por
condições de integração ou contexto. JAR Spring Boot empacotado. A opção do JDK
contorna a limitação local de sockets temporários do Windows usando fallback TCP;
não foi adicionada à configuração do projeto.

Os testes de cargos incluem 9 de controller, 17 de service e 2 da cadeia real de
segurança, além de autorização por proxy, repository, configuração e importação.
Confirmam as sete chamadas sem UID do cliente, rejeição de autenticação ausente,
inválida ou perfil sem permissão, principal correto para ambos os gestores,
query legada sem efeito na identidade e PATCH sem corpo. O teste de OpenAPI
confere ausência do UID nos DTOs/query e ausência de corpo no PATCH.

`git diff --check`: aprovado. Revisão do diff concluída sem achados acionáveis
em aberto no escopo de cargos. Não há melhorias opcionais necessárias ao pedido.
