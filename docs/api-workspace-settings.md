# API de Configurações de Workspace — SCRUM-584

Implementação das subtarefas [SCRUM-585](https://luizacursino.atlassian.net/browse/SCRUM-585),
[SCRUM-586](https://luizacursino.atlassian.net/browse/SCRUM-586),
[SCRUM-587](https://luizacursino.atlassian.net/browse/SCRUM-587),
[SCRUM-588](https://luizacursino.atlassian.net/browse/SCRUM-588) e
[SCRUM-589](https://luizacursino.atlassian.net/browse/SCRUM-589).

## Dependência de banco antes do deploy

A [documentação PostgreSQL](https://astro-inter.github.io/banco-de-dados/#/database)
consultada em 18/10/2026 mostra sete colunas em `unidade_endereco`, sem
`numero`. Esta implementação mapeia `numero VARCHAR(30)`, permitindo NULL
para endereços legados. Novas requisições de criação/edição exigem o número.

A alteração de schema deve ser preparada e publicada no repositório do banco
antes desta versão da API. Exemplo da alteração mínima necessária:

```sql
ALTER TABLE public.unidade_endereco
    ADD COLUMN numero VARCHAR(30);
```

Não foi executado SQL contra o banco. Não há migração automática neste projeto.
Revisar o schema efetivo antes de aplicar a alteração e não preencher números
legados com valores inventados.

`GET /units` mantém `fn_seleciona_unidades(uid)` como fonte de autorização e
dos campos existentes, acrescentando um LEFT JOIN com `unidade_endereco`
para obter `numero`. Assim, não exige mudar a assinatura da função.
A coluna também será necessária para as consultas JPA de endereço, inclusive
nos fluxos existentes. Não disponibilizar a API antes dessa alteração.

## Autorização e rotas

Todas as cinco rotas exigem `Authorization: Bearer <token Firebase>` e
`GESTOR_WORKSPACE` ativo. O UID vem do principal; o workspace é obtido pelo
usuário persistido, sem aceitar um workspace arbitrário no body.
Pagamentos, edição da conta e exclusão/desativação de unidades não fazem parte
deste contrato. As consultas existentes de unidades mantêm seus perfis atuais.

| Método | Rota | Sucesso |
| --- | --- | --- |
| GET | /workspace | 200 com dados atuais da empresa |
| PUT | /workspace | 200 com empresa atualizada |
| POST | /workspace/import | 200 com resumo da sincronização |
| POST | /units | 201 com nova unidade e Location |
| PUT | /units/{unitId} | 200 com unidade atualizada |

As respostas usam `ApiResult`. A ausência de autenticação nessas rotas retorna
401; perfil ou vínculo não autorizado retorna 403. Dados inválidos retornam
400; conflitos de unicidade retornam 409; unidade fora do workspace retorna 404.
Falhas de infraestrutura seguem o handler global.

## Empresa

GET e PUT retornam `data` com `id`, `name` e `cnpj`.
O PUT recebe:

```json
{"name":"Empresa Exemplo","cnpj":"12345678000199"}
```

Nome tem de 2 a 255 caracteres após trim. CNPJ deve conter 14 dígitos;
pontuação usual é removida. A validação atual verifica formato, sem calcular
dígitos verificadores, conforme o cadastro existente.
A checagem de duplicidade exclui o próprio ID; a restrição única do PostgreSQL
continua protegendo concorrência. Nome/CNPJ não recriam a empresa ou o gestor.

## Unidades

POST e PUT recebem o DTO do cadastro, com nomes em inglês:

```json
{
  "name": "Filial",
  "cep": "01234567",
  "state": "SP",
  "city": "São Paulo",
  "neighborhood": "Centro",
  "street": "Rua Exemplo",
  "number": "10A",
  "addressLine2": "Sala 1"
}
```

Nome/cidade/bairro/rua têm de 2 a 255 caracteres; número tem de 1 a 30.
CEP é normalizado para oito dígitos; UF é convertida para maiúsculas e
validada contra as UFs brasileiras. Complemento é opcional, até 255 caracteres;
valor vazio remove o complemento na edição.

Unidade e endereço são persistidos na mesma transação. O nome é comparado
após trim, remoção de acentos e conversão para minúsculas. A edição exclui o
próprio ID na checagem e preserva status, workspace e vínculos de NRs,
colaboradores e eventos.

As respostas usam os campos existentes de `UnitResponse` em português,
acrescentando `numero`, sem anotações `@JsonProperty`; os componentes do DTO
usam diretamente os nomes do contrato existente. A listagem `GET /units` também retorna esse campo,
que pode ser null em registros legados.

## Sincronização de planilha

Multipart com arquivo XLS/XLSX na parte `file`, até 10 MiB.
O fluxo inicial `POST /register-workspace` mantém seu limite atual de 5 MiB.
A parte JSON `data` é opcional quando todas as unidades já existem:

```json
{
  "units": [
    {
      "name":"Filial",
      "cep":"01234567",
      "state":"SP",
      "city":"São Paulo",
      "neighborhood":"Centro",
      "street":"Rua Exemplo",
      "number":"10A",
      "addressLine2":null
    }
  ]
}
```

Enviar `Content-Type: application/json` na parte `data`.
O Excel contém as seis colunas: Nome, CPF, Email, Unidade, Cargo e Modalidade.
A primeira aba é lida; modalidades aceitas: Presencial, Remoto e Híbrido.
Os nomes de cargos e unidades são resolvidos por trim, remoção de acentos e
minúsculas, sempre dentro do workspace.

| Registro enviado | Resultado |
| --- | --- |
| CPF novo e email livre | COLABORADOR/PRE_CADASTRADO, sem Firebase UID ou senha |
| CPF existente no workspace, dados iguais | Sem update e sem convite |
| CPF existente com alterações | Atualiza nome, email, modalidade, cargo e unidade |
| Pessoa ausente do arquivo | Permanece intacta |
| Gestor identificado no workspace | Ignorado com motivo; sem recriação ou rebaixamento |
| Linha idêntica repetida | Ignorada com indicação da linha |
| CPF repetido com dados diferentes ou email de outra pessoa | Erro por linha |
| CPF de outro workspace | Erro; sem transferência de empresa |
| Cargo novo | Criado ativo, conforme regra confirmada pelo usuário |
| Unidade nova | Criada com endereço recebido em data.units |
| Unidade fornecida e já existente | Reutilizada; o endereço não é alterado pelo import |

Unidades explicitamente enviadas podem ser criadas mesmo sem colaboradores
naquele arquivo. Cargos novos são criados apenas quando usados por linhas
válidas que serão gravadas, sem NRs automáticas, como no cadastro inicial.
Unidade desconhecida sem endereço gera erro no campo `unidade`, com seu nome,
antes de qualquer gravação. Um novo nome no Excel não renomeia uma sede existente.

CPF é a chave desta sincronização; correções do próprio CPF usam a edição por
ID. CPF/email são validados e conflitos não unem identidades automaticamente.
As atualizações preservam ID, Firebase UID, tipo, status e criado_em, mantendo
as referências do histórico. Não há ativação automática de pessoas existentes.
Novos vínculos exigem unidade/cargo ativos; vínculos inativos que não mudaram
podem ser mantidos.

O processamento valida o plano completo antes de gravar. Mutações do workspace
usam lock da empresa; identidades existentes são bloqueadas em ordem de ID.
Novos vínculos usam locks compartilhados de cargo/unidade para conferir o estado
ativo no momento da gravação. As alterações PostgreSQL são uma transação.

Se houver mudança de email de usuário já vinculado ao Firebase, a identidade
remota também é atualizada. Em falha de escrita ou commit do PostgreSQL,
as mudanças remotas são compensadas em ordem inversa. Se a compensação falhar,
o serviço tenta desativar o colaborador para conter uma identidade inconsistente,
registra a necessidade de reconciliação e retorna erro. Não há transação
distribuída entre PostgreSQL, Firebase e Redis.

Somente novos colaboradores geram o evento de convite, consumido pela fila
existente após commit. A fila atual registra falhas Redis sem reverter o commit;
este trabalho não adiciona mecanismo de outbox/reprocessamento.

Exemplo de `data` em sucesso:

```json
{
  "created": 2,
  "updated": 1,
  "unchanged": 4,
  "unitsCreated": 1,
  "rolesCreated": 1,
  "ignored": [{"row":9,"field":"cpf","message":"Linha idêntica repetida"}]
}
```

HTTP 422 tem `success: false` e `data` com a lista de erros estruturados:

```json
[{"row":4,"field":"unidade","message":"Informe o endereço completo da nova unidade: Filial"}]
```

Erros de formato/estrutura do arquivo retornam 400; tamanho excedido retorna 413.
Repetir a mesma importação bem-sucedida não duplica pessoas, cargos ou unidades
e não reenvia convites dos registros existentes.

## Organização e verificação

`WorkspaceSettingsController` mantém as rotas autenticadas separadas do
cadastro público. `WorkspaceAccessService` resolve o escopo comum;
`WorkspaceSettingsService` edita a empresa; `UnitSettingsService` compartilha
a criação de sedes com o import. `WorkspaceImportService` coordena o lote e
a compensação Firebase; `CollaboratorImportService` valida, compara e persiste
os colaboradores usando `CollaboratorMapper`. A leitura Excel fica em
`CollaboratorSpreadsheetReader`, com o modelo `CollaboratorImportRow`,
compartilhados com o cadastro inicial de `SpreadsheetImportService`.
A resolução e criação de cargos passam por `RoleService`; unidades e endereços
ficam em `UnitSettingsService`. `WorkspaceMapper` e `UnitResponseMapper`
concentram as conversões das respostas. Repositories mantêm consultas e locks;
validações, comparações e decisões de importação pertencem aos services.

Os testes cobrem contratos HTTP, perfil de acesso, isolamento, conflitos,
preservação de identidade/status, repetições, novas sedes/cargos e compensação
Firebase inclusive quando o commit falha. Os testes unitários usam mocks e não
substituem execução contra PostgreSQL/Firebase com o schema atualizado.


## Revisão de arquitetura

A revisão comparou esta demanda com os módulos já implementados, respeitando
as regras de estilo pedidas para esta entrega.

| Responsabilidade | Exemplo existente | Aplicação nesta demanda |
| --- | --- | --- |
| HTTP, validação de entrada e autorização por perfil | `ManagerController`, `CollaboratorController` | Controllers de configurações delegam aos services, usam `ApiResult` e recebem o principal autenticado; os retornos ficam separados em etapas. |
| Conversão de entidades e DTOs | `ManagerMapper`, `ConformityMapper` | `WorkspaceMapper`, `UnitMapper`, `UnitResponseMapper` e `CollaboratorMapper` concentram as conversões. |
| Regras, escopo e transações | `ManagerService`, `CollaboratorService` | Services conferem o gestor ativo, obtêm o próprio workspace, comparam os registros e decidem o que criar, atualizar ou ignorar. |
| Leitura de Excel separada das regras | `ConformitySpreadsheetReader`, `ConformityImportService` | O reader de colaboradores lê o arquivo; os services de importação validam e coordenam a gravação. |
| Consultas e bloqueios no repository | `ManagerRepository.findUserForUpdateInWorkspace` | As consultas de escrita usam bloqueios explícitos e SQL curto; repositories não decidem como processar linhas da planilha. |
| Sincronização com autenticação externa | `ManagerService.update`, `CollaboratorService.update` | `TransactionTemplate` cobre escrita e commit; falhas acionam compensação Firebase. |
| Convites após confirmação no banco | `WorkspaceRegistrationTransactionalService`, `PreRegistrationQueueService` | O mesmo evento existente é publicado para novos colaboradores e consumido após commit. |

Foi corrigida uma dependência do modelo de importação em DTOs HTTP:
`CollaboratorImportData` pertence a `user.model`, enquanto
`SpreadsheetImportError` e `SpreadsheetImportException` são tipos compartilhados
em `common.model` e `common.exception`. O modelo interno não importa DTOs,
controllers ou services. O JSON dos erros mantém `row`, `field` e `message`.

A importação de workspace usa uma transação para o lote inteiro; a importação
de conformidades usa transações por linha. Essa diferença atende às regras de
cada fluxo e não altera a separação das camadas. A revisão não reescreve módulos
antigos que tenham convenções de estilo diferentes.
