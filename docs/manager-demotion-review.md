# Revisão do rebaixamento de gestores — SCRUM-525

Revisão do diff da branch `feat/SCRUM-525-implementar-endpoints-gestores`
contra `origin/main`, em 01/11/2026, seguindo `astro-api-development` e
`astro-code-review`. Não foram identificados achados acionáveis em aberto
no escopo da alteração.

## Áreas revisadas

- Controller, service, mapper existente, consulta de bloqueio e DTO de resposta.
- Restrição `GESTOR_WORKSPACE`, revalidação do solicitante ativo, isolamento de
  workspace e proteção do perfil do responsável pelo workspace.
- Transição exclusiva de `GESTOR` para `COLABORADOR`, sem alteração de status,
  identidade Firebase, dados pessoais, cargo, unidade ou identificador.
- Transação na camada de serviço, lock pessimista, rollback e concorrência.
- Respostas `ApiResult`, erros globais, ausência de campos sensíveis,
  anotações OpenAPI e documentação em `api-managers.md`.
- Testes unitários, MockMvc, cadeia real de Spring Security, documento OpenAPI
  gerado e integração PostgreSQL isolada, inclusive sua configuração no CI.

O repository reutiliza SQL existente com parâmetros nomeados, escopo de
workspace e `FOR UPDATE OF u`; um método derivado não substitui esse bloqueio.
O mapeamento continua no mapper do domínio. A implementação nova usa tipos
explícitos, campos em inglês e não adiciona comentários ao código-fonte.
Não há novo efeito externo em Firebase, Redis ou R2; a leitura de foto reutiliza
o serviço existente. A transação não é apresentada como atômica com esses serviços.

## Verificações executadas

- `.\mvnw.cmd -o --batch-mode --no-transfer-progress -Dtest=ManagerServiceTest,ManagerControllerTest,ManagerSecurityTest,ManagerDemotionPostgresTest,SwaggerDocumentationTest test`:
  **38 testes, zero falhas, zero erros, nenhum ignorado**, com Java 21.
- Seis desses testes usam PostgreSQL 16 local descartável: os três status da
  conta, alvos protegidos/outro workspace, rollback após flush e duas
  rebaixamentos concorrentes. Conferem as colunas da conta antes/depois.
- MockMvc confere a rota PATCH sem corpo e os erros HTTP. A cadeia real de
  segurança rejeita os perfis sem permissão. O mesmo token deixa de acessar
  uma rota de gestor quando o perfil atual passa a ser colaborador.
- O teste de OpenAPI gera `target/astro-openapi.json` e verifica a operação,
  ausência de request body e respostas 200/400/403/404.
- `git diff --check`: aprovado.

A primeira execução da suíte geral dentro do sandbox foi interrompida após
travar na inicialização do Mockito; a execução focada acima foi concluída fora
dele. O PostgreSQL é exclusivo dos testes, sem conexão com bancos de produção.

## Suíte geral e empacotamento

A execução padrão `.\mvnw.cmd --batch-mode --no-transfer-progress verify`
foi concluída com um erro ambiental em `ChatPushConfigurationTest`:
`HttpClient` não conseguiu criar o socket temporário Unix no Windows
(`Unable to establish loopback connection`). Os demais testes não falharam.

A execução final usou:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress '-DargLine=-Djdk.net.unixdomain.tmpdir=C:/codex-unavailable-uds' verify
```

O diretório indicado não existe. Conforme o fallback do JDK 21, a criação do
pipe usa loopback TCP. A opção foi aplicada somente ao processo de testes,
sem alteração no código da aplicação ou na configuração do projeto.
Com as variáveis do PostgreSQL descartável, o resultado foi **BUILD SUCCESS**:
**744 testes, zero falhas, zero erros e 59 ignorados** por condições de integração
local/contexto desabilitado. Os seis testes PostgreSQL do rebaixamento executaram
e passaram; o JAR Spring Boot foi empacotado. O teste de notificações que havia
falhado passou nessa execução final.

## Não verificado

Não foi exercitado login Firebase real nem rebaixamento em um ambiente publicado.
Os contratos HTTP são testados localmente com MockMvc; não foi iniciado um
servidor HTTP separado com as integrações de produção. As integrações de outros
domínios dependem de suas variáveis e serviços próprios.

## Melhorias opcionais

Nenhuma melhoria adicional é necessária para o escopo solicitado.

## Complemento: terminologia “rebaixar”

O ajuste posterior à integração das PRs originais altera mensagens, resumo e
resposta OpenAPI e documentação. A revisão deste diff não identificou achados
acionáveis em aberto. A rota, os DTOs e as regras de negócio foram preservados.

Validação deste ajuste: `.\mvnw.cmd -o --batch-mode --no-transfer-progress -Dtest=ManagerControllerTest,SwaggerDocumentationTest -DargLine=-Djdk.net.unixdomain.tmpdir=C:/codex-unavailable-uds test`:
**7 testes aprovados**, zero falhas/erros e nenhum ignorado. `git diff --check`
aprovado. Os testes PostgreSQL e a suíte geral descritos acima pertencem à
validação da implementação original; não foram repetidos para a troca textual.
