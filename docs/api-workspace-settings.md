# API de Configurações de Workspace — SCRUM-584

Implementação das subtarefas [SCRUM-585](https://luizacursino.atlassian.net/browse/SCRUM-585),
[SCRUM-586](https://luizacursino.atlassian.net/browse/SCRUM-586),
[SCRUM-587](https://luizacursino.atlassian.net/browse/SCRUM-587),
[SCRUM-588](https://luizacursino.atlassian.net/browse/SCRUM-588) e
[SCRUM-589](https://luizacursino.atlassian.net/browse/SCRUM-589).

Inclui também a transferência de responsabilidade do workspace para outro gestor
ativo, conforme as regras confirmadas em 19/10/2026.

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

Não foi executado SQL contra o banco da aplicação. Os testes de integração usam
somente um PostgreSQL local descartável. Não há migração automática neste projeto.
Revisar o schema efetivo antes de aplicar a alteração e não preencher números
legados com valores inventados.

`GET /units` mantém `fn_seleciona_unidades(uid)` como fonte de autorização e
dos campos existentes, acrescentando um LEFT JOIN com `unidade_endereco`
para obter `numero`. Assim, não exige mudar a assinatura da função.
A coluna também será necessária para as consultas JPA de endereço, inclusive
nos fluxos existentes. Não disponibilizar a API antes dessa alteração.

## Autorização e rotas

Todas as seis rotas exigem `Authorization: Bearer <token Firebase>` e
`GESTOR_WORKSPACE` ativo. O UID vem do principal; o workspace é obtido pelo
usuário persistido, sem aceitar um workspace arbitrário no body.
Pagamentos, edição da conta e exclusão/desativação de unidades não fazem parte
deste contrato. As consultas existentes de unidades mantêm seus perfis atuais.

| Método | Rota | Sucesso |
| --- | --- | --- |
| GET | /workspace | 200 com dados atuais da empresa |
| PUT | /workspace | 200 com empresa atualizada |
| POST | /workspace/import | 200 com resumo da sincronização |
| POST | /workspace/manager-transfer | 200 com IDs do workspace e dos dois gestores |
| POST | /units | 201 com nova unidade e Location |
| PUT | /units/{unitId} | 200 com unidade atualizada |

As respostas usam `ApiResult`. A ausência de autenticação nessas rotas retorna
401; perfil ou vínculo não autorizado retorna 403. Dados inválidos retornam
400; conflitos de unicidade retornam 409; unidade fora do workspace retorna 404.
Falhas de infraestrutura seguem o handler global.

## Transferência do gestor responsável

`POST /workspace/manager-transfer` recebe:

```json
{"newManagerId": 42}
```

O ID é obrigatório e positivo. O workspace e o responsável atual são obtidos da
identidade autenticada; não são parâmetros da requisição.

- Somente o `GESTOR_WORKSPACE` ativo pode transferir a responsabilidade.
- O escolhido precisa ser outro usuário, do mesmo workspace, com tipo `GESTOR`,
  status `ATIVO` e autenticação concluída (Firebase UID preenchido).
- O responsável anterior passa a `GESTOR`; o escolhido passa a `GESTOR_WORKSPACE`.
- Somente `usuario.tipo` é atualizado. Identidade, status, cargo profissional,
  unidade, datas, histórico e demais vínculos são preservados.
- A API exige um único responsável existente antes da operação e serializa as
  transferências pelo bloqueio do workspace. O novo gestor também é bloqueado.
- As duas mudanças acontecem na mesma transação. Uma falha na promoção desfaz
  a mudança anterior; outros consumidores não observam um estado intermediário.
- A gravação confere novamente os tipos e o status atuais no banco. Uma
  requisição que leu a autorização antes de outra transferência terminar não
  pode transferir novamente em nome do antigo responsável.

Exemplo do conteúdo de `data` na resposta:

```json
{"workspaceId": 3, "previousManagerId": 8, "newManagerId": 42}
```

Dados inválidos, escolha da própria conta ou gestor inativo retornam 400.
Conta de outro workspace ou inexistente retorna 404. Perfil escolhido diferente
de `GESTOR`, responsabilidade ambígua ou conflito na promoção retornam 409.
Solicitante sem autorização atual retorna 403; sem autenticação retorna 401.

O seletor da interface pode reutilizar `GET /managers`, mantendo apenas os
resultados com `status = ATIVO`. Essa listagem já retorna ID e e-mail e consulta
somente gestores comuns do workspace; o envio usa o ID, mesmo exibindo o e-mail.
O backend repete as validações na confirmação, independentemente da listagem.

`FirebaseAuthenticationFilter` chama `AuthorizationService` a cada requisição.
A [função de autorização documentada](https://github.com/Astro-Inter/banco-de-dados/blob/main/database/functions/03_fn_retornar_nivel_acesso.sql)
lê `usuario.tipo`; não é necessário alterar credenciais ou claims do Firebase.
Após o sucesso, o frontend deve atualizar o perfil exibido e a navegação do antigo
responsável. Não há envio de convite nem criação de outra conta neste fluxo.

`WorkspaceManagerTransferPostgresTest` valida a troca, o rollback integral e uma
requisição concorrente com a autorização antiga em um schema exclusivo do banco
local descartável. O CI habilita o teste com `WORKSPACE_TEST_JDBC_URL`.

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

POST e PUT consultam o Geoapify internamente para preencher
`unidade_endereco.geolocalizacao`. Uma falha na busca não impede salvar a unidade;
o PUT permite tentar novamente. Ao mudar o endereço sem conseguir localizá-lo,
as coordenadas antigas são limpas. Consulte [geolocalização de unidades](unit-geolocation.md)
para configuração e regras completas.

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

A busca para edição usa o método derivado
`UnitRepository.findByIdAndWorkspace_Id`, com `@Lock(PESSIMISTIC_WRITE)`.
O Spring Data gera o filtro por ID e workspace; o SQL dessa busca não é escrito
manualmente. O bloqueio é mantido até o término da transação.
`UnitRepositoryPostgresTest` confere o escopo, endereços opcionais e a contenção
de uma atualização concorrente em outra conexão. No CI, ele reutiliza o banco
PostgreSQL descartável disponível, sempre dentro de um schema próprio.

As respostas de criação, edição e listagem usam os campos de `UnitResponse` em
inglês: `unitId`, `workspaceId`, `name`, `status`, `collaboratorCount`, `postalCode`,
`street`, `city`, `neighborhood`, `state`, `addressLine2` e `number`.
Não há anotações `@JsonProperty`; o JSON usa diretamente os nomes do DTO.
Consumidores que liam os nomes anteriores em português precisam atualizar a
integração. `number` corresponde à coluna `numero` e pode ser null em registros legados.

Nas respostas de criação e edição, `collaboratorCount` usa o método derivado
`UserRepository.countByUnit_IdAndTypeInAndStatus`. O service considera os tipos
`COLABORADOR`, `GESTOR` e `GESTOR_WORKSPACE`, somente com status `ATIVO` e na mesma
unidade, em conformidade com `vw_unidades`. O método derivado não exige uma
`@Query` escrita manualmente. Unidades inativas mantêm a contagem de seus usuários ativos.

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

A leitura usa o mesmo `CollaboratorSpreadsheetReader` do cadastro inicial.
Os dois fluxos também usam `CollaboratorMapper` para converter a modalidade e
montar o colaborador, e `CollaboratorSpreadsheetValidator` para validar os dados
pessoais. A validação mantém as diferenças existentes: o cadastro inicial exige
nome não vazio e e-mail com domínio pontuado; a sincronização mantém os limites
de tamanho e as constraints de `CollaboratorImportData`. Erros do cadastro
continuam limitados ao primeiro erro de cada linha, com as mesmas mensagens.
A modalidade inválida continua sendo reportada após conferir os vínculos no
cadastro inicial.

`PersonalDataNormalizer` centraliza nome, CPF e e-mail também nos DTOs de criação
e edição individual. A limpeza legada do CPF no cadastro inicial continua
removendo todos os caracteres que não são dígitos; os outros fluxos removem
somente a pontuação prevista. A comparação de nomes no cadastro inicial e nos
cargos usa o mesmo `NormalizedText.key` já utilizado pela sincronização.

Na gravação, a importação também reaproveita os caminhos do cadastro:

- Novos colaboradores passam por `UserBatchService.saveAllPreRegistered`, com
  `JdbcTemplate.batchUpdate` em grupos de até 500 registros.
- Colaboradores existentes alterados são atualizados separadamente, preservando
  IDs, Firebase UID e status, com um `saveAllAndFlush` para o conjunto alterado.
- Novas unidades e endereços usam `UnitService.createForWorkspace`, que salva
  as coleções de unidades e endereços com `saveAll`.
- Cargos novos usam `saveAll`, como no cadastro, depois de validar todos os
  nomes; não há `saveAndFlush` por cargo.

As gravações continuam dentro da transação do lote. Usar `saveAll` para cargos
ou unidades não significa um único INSERT SQL: entidades com IDs gerados por
`IDENTITY` ainda podem exigir inserts individuais. A mudança remove os flushes
explícitos por cargo e compartilha o batch JDBC já existente para colaboradores.

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
a criação de sedes com o import por meio de `UnitService.createForWorkspace`. `WorkspaceImportService` coordena o lote e
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
`SpreadsheetImportMessage` e `SpreadsheetImportException` são tipos compartilhados
em `common.model` e `common.exception`. O modelo interno não importa DTOs,
controllers ou services. O JSON dos erros mantém `row`, `field` e `message`.

A importação de workspace usa uma transação para o lote inteiro; a importação
de conformidades usa transações por linha. Essa diferença atende às regras de
cada fluxo e não altera a separação das camadas. A revisão não reescreve módulos
antigos que tenham convenções de estilo diferentes.
