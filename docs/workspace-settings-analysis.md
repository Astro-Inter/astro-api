# Análise de endpoints para Configurações > Workspace

Este documento registra o levantamento anterior à implementação. Os contratos
implementados e a dependência de schema estão em
[api-workspace-settings.md](api-workspace-settings.md). O usuário confirmou
posteriormente a criação automática dos cargos novos encontrados na planilha.

Análise em 18/10/2026, baseada na navegação autenticada em
https://astro-inter.github.io/astro-web e no código deste checkout da API.
Pagamentos e a aba Conta estão fora do escopo. Nenhuma alteração de dados foi
salva no site e nenhum upload foi realizado. As rotas abaixo são propostas,
não funcionalidades já implementadas. Não foi verificada a versão implantada
da API nem o schema real do PostgreSQL.

Regra esclarecida pelo usuário após a inspeção: colaboradores existentes sem
mudanças devem ser ignorados; existentes com dados alterados devem ser
atualizados; novos colaboradores e novas unidades também precisam ser
considerados. A importação é uma sincronização dos registros enviados.

## Fluxos observados

| Ação | Tela | Campos e comportamentos |
| --- | --- | --- |
| Alterar informações | `includeCompanyInformation?context=settings` | Nome da empresa e CNPJ; salvar alterações. |
| Adicionar planilha | `attachExcelFile?context=settings` | Adicionar colaboradores por XLS/XLSX; nome, CPF, email, unidade, cargo e modalidade; limite informado de 10 MB; explicação e botão para baixar modelo. |
| Alterar unidades | `setAddress?context=settings` | Nome, CEP, estado, cidade, bairro, rua, número e complemento; navegação entre duas sedes existentes; adicionar outra sede; remover sede; salvar alterações. |

As três ações pedem a senha atual antes de abrir a tela. Isso evidencia uma
etapa de confirmação de identidade na interface; a navegação, sozinha, não
comprova como o frontend a implementa nem que a API imponha essa etapa.

## Endpoints novos propostos

Cinco rotas cobrem consulta da empresa, edição, sincronização por planilha e
criação/edição de unidades. A remoção de uma sede já persistida depende da
definição de negócio descrita adiante.

| Método e rota proposta | Módulo | Contrato sugerido |
| --- | --- | --- |
| `GET /workspace` | `workspace` | Retornar `id`, `name` e `cnpj` do workspace do usuário autenticado para preencher o formulário. |
| `PUT /workspace` | `workspace` | Receber `name` e `cnpj`; atualizar a empresa existente e retornar os dados atualizados. |
| `POST /workspace/import` | `workspace`, coordenando `user`, `unit` e `role` | Receber multipart com `file` e, quando houver novas unidades, JSON `data` com seus dados; criar, atualizar ou ignorar colaboradores e resolver/criar unidades no workspace existente. |
| `POST /units` | `unit` | Receber nome e endereço completo; criar uma unidade no workspace existente e retornar seu ID. |
| `PUT /units/{unitId}` | `unit` | Receber todos os campos editáveis de nome/endereço; preservar ID, workspace, status e associações de NRs. |

Usar `ApiResult` e token Firebase, conforme os contratos existentes. O workspace
deve ser resolvido pelo UID autenticado e pelo vínculo do usuário no banco.
Um ID enviado no body não deve determinar o escopo de autorização.
Recomendação inicial: reservar a administração destas configurações a
`GESTOR_WORKSPACE`; eventual acesso de `GESTOR` precisa ser definido de forma
explícita. Não inferir permissão de edição a partir do acesso atual de leitura.

### Informações da empresa: módulo workspace

Implementar a consulta e a edição em `WorkspaceController`/`WorkspaceService`,
ou separar um `WorkspaceSettingsController`/`WorkspaceSettingsService` dentro
do mesmo módulo para preservar a separação com o cadastro público.
Criar `WorkspaceResponse` e `UpdateWorkspaceRequest`.

`Workspace` já contém nome e CNPJ. `WorkspaceRepository` já possui
`existsByCnpj`, mas a edição precisa ignorar o próprio workspace na checagem
de duplicidade. Definir validação e normalização de CNPJ e garantir unicidade
também no banco. `GET /user/me` não supre a consulta: seu DTO não retorna nome
da empresa nem CNPJ.

Não reutilizar `POST /register-workspace`: ele é público, cria usuário no
Firebase e persiste um workspace novo.

### Sincronização por planilha: coordenação em workspace

Com a regra esclarecida pelo usuário, a proposta passa a ser
`POST /workspace/import`, em um controller autenticado de workspace, coordenado
por `WorkspaceImportService`. A operação envolve colaboradores e unidades do
mesmo workspace. Dentro de `user`, um `CollaboratorImportService` fica
responsável por identificar, comparar, criar e atualizar colaboradores.
Dentro de `unit`, `UnitService` resolve unidades existentes e cria as novas.
`RoleService` resolve os cargos necessários. Não criar outro workspace nem
outro gestor, e não fazer chamadas HTTP entre os módulos.

Reutilizar as regras de criação e edição de `CollaboratorService`, o insert
em lote de `UserBatchService` para os novos e o evento
`PreRegisteredCollaboratorsCreatedEvent` somente para novos colaboradores.
Preservar o envio à fila após o commit. `POST /collaborators/import` seria uma
alternativa para um fluxo limitado a colaboradores com unidades já persistidas;
não é necessário implementar ambas as rotas para esta tela.

`workspace/service/SpreadsheetImportService` já lê as seis colunas exigidas,
normaliza valores e valida dados. A leitura pode ser extraída para um componente
compartilhado por cadastro inicial e importação adicional. A validação atual
depende de uma linha do gestor e rejeita usuários já existentes; não pode ser
reutilizada integralmente como fluxo de atualização.

Comportamento exigido e critérios de implementação sugeridos:

| Situação | Resultado |
| --- | --- |
| Colaborador não cadastrado | Criar como `COLABORADOR`/`PRE_CADASTRADO`, sem senha, e enfileirar convite após o commit. |
| Colaborador existente com os mesmos dados normalizados | Ignorar a linha, sem update nem novo convite. |
| Colaborador existente com mudanças | Atualizar nome, email, cargo, unidade e modalidade informados, preservando a identidade e o histórico. |
| Colaborador ausente do arquivo | Manter como está; ausência não significa exclusão ou desativação. |
| Unidade existente no workspace | Reutilizar seu ID; vincular novos colaboradores ou transferir existentes para ela conforme a planilha. |
| Unidade nova | Criar com os dados complementares de endereço e vincular os colaboradores aos IDs criados. |

Propor CPF normalizado como chave de identificação de colaboradores dentro do
workspace, pois o email pode mudar. O CPF identifica o registro nesta importação;
sua correção exige edição explícita por ID ou outra regra de reconciliação.
Não usar nome como identidade nem unir pessoas automaticamente por email.
CPF fora do workspace, email pertencente a outra pessoa, CPF/email cruzados e
linhas duplicadas conflitantes devem produzir erro por linha, nunca alterar
outra conta. Linhas idênticas repetidas podem ser ignoradas com indicação no
resumo. Um gestor eventualmente presente no arquivo não deve ser recriado,
rebaixado a colaborador nem atualizado por uma rotina exclusiva de colaboradores;
identificar e informar essa linha como fora do escopo de alteração.

Preservar ID, Firebase UID, tipo da conta, data de criação e status dos existentes,
além de referências a eventos, evidências e histórico. A planilha não contém status,
logo sua importação não deve ativar pessoas automaticamente. Reutilizar as regras
existentes de unidade/cargo ativos para novos vínculos; vínculos inativos que não
mudaram podem ser preservados, como na edição atual. Se o email de uma conta
vinculada ao Firebase mudar, coordenar também essa atualização com compensação
em caso de falha, conforme a regra de edição existente.

Validar o arquivo inteiro e montar um plano antes de gravar. As alterações no
PostgreSQL devem compor uma transação para colaboradores, unidades e endereços.
Não chamar `CollaboratorService.update` em loop sem refatoração: ele usa
`PROPAGATION_REQUIRES_NEW` e confirmaria linhas separadamente. Extrair regras
compartilhadas e coordenar as alterações de email e suas compensações no lote.
PostgreSQL e Firebase não compartilham uma transação; falhas de compensação
precisam de reconciliação e não permitem prometer atomicidade entre os serviços.

Retornar contagens como `created`, `updated`, `unchanged`, `unitsCreated`,
além de linhas ignoradas com motivo e erros estruturados por `row`, `field` e
`message`. Repetir o mesmo arquivo após uma importação bem-sucedida deve resultar
em registros sem mudanças, sem duplicar pessoas/unidades nem reenviar convites.

### Novas unidades e dados complementares

O Excel observado traz somente o nome da unidade, sem endereço. Quando uma
linha citar uma unidade que ainda não existe, o fluxo precisa coletar nome,
CEP, estado, cidade, bairro, rua, número e complemento em uma etapa como a do
cadastro inicial. Não inventar endereço nem simplesmente rejeitar a unidade
nova como se ela não pudesse ser cadastrada.

Contrato proposto: multipart `file` com o Excel e parte JSON `data` com `units`
contendo as definições das novas sedes. Unidades existentes são resolvidas por
ID quando fornecido e por nome normalizado no escopo do workspace para as
referências textuais da planilha; unidades novas são criadas antes de gravar os
vínculos. Se a sede já foi salva em `POST /units`, apenas reutilizar o ID.
Não interpretar um nome diferente como renomeação de uma sede existente.

Se faltarem dados de uma unidade nova, retornar uma lista estruturada das
unidades a completar, sem persistir parcialmente. O frontend pode detectar os
nomes desconhecidos ao comparar a planilha com `GET /units`, exibir o formulário
e enviar tudo na confirmação final. Um endpoint separado de pré-validação é
opcional; não é necessário para implementar o processamento final. Validar
novamente as referências e a unicidade no backend durante a gravação.

Criar/reutilizar cargos conforme uma política explícita: o cadastro inicial
cria cargos encontrados na planilha, mas a extensão dessa regra para esta
sincronização ainda precisa ser definida. Isso não impede considerar novas
unidades, já confirmado pelo usuário.

O limite da interface é 10 MB; `SpreadsheetImportService.read` limita a 5 MB.
Alinhar ambos. O limite global de multipart é maior e não elimina essa validação.
O modelo pode continuar sendo um arquivo estático; seu download não exige,
por si só, uma rota nova. `POST /conformities/import` importa outro domínio e
não atende esta tela.

O cadastro documenta HTTP 422 para erros de planilha, mas
`SpreadsheetImportException` não possui um handler específico no código
inspecionado. Ao aproveitar essa exceção, implementar o contrato de erro
estruturado, pois o handler genérico atual trata exceções não mapeadas como 500.

### Unidades: módulo unit

Ampliar `UnitController` e `UnitService`, com DTOs específicos para criação e
atualização. Reaproveitar `Unit`, `UnitAddress`, repositories e mappers.
Persistir unidade e endereço na mesma transação. Validar nome duplicado no
workspace e impedir alteração de unidades de outra empresa.

`GET /units` já lista IDs, nomes e endereços: reutilizá-lo para carregar o editor.
`GET /units/{unitId}` seria opcional, não uma necessidade desta tela.

O campo número requer correção em todo o caminho: `UnitRequest` o recebe e
exige, mas `UnitMapper.toAddress` não o mapeia; `UnitAddress`, `UnitProjection`
e `UnitResponse` também não o representam. Conferir a tabela e a função
`fn_seleciona_unidades` no banco para determinar os ajustes SQL necessários.
O repositório da API não permite concluir se a coluna já existe no banco real.

O botão único de salvar pode exigir várias chamadas para criar e editar sedes.
Se o produto exigir que todas sejam salvas ou rejeitadas juntas, uma alternativa
é uma rota de lote, como `PUT /workspace/units`, no módulo `unit`, coordenando
criações e atualizações em uma transação. Ela pode substituir as mutações
individuais para esta tela; não é necessário implementar as duas opções
apenas para atender o mesmo fluxo. Preservar IDs das sedes existentes.

## Remover sede: definição pendente

Remover uma sede nova, ainda não salva, é uma operação local do formulário.
Para uma sede persistida, a interface não esclarece se significa desativar,
excluir ou transferir pessoas e vínculos. Não presumir exclusão física.

Já existe `PATCH /units/{unitId}/status`. Ele alterna o status, sem body;
portanto, não equivale a um comando idempotente para desativar. A documentação
de unidades também informa efeitos de desativação sobre usuários vinculados.
Se a regra escolhida for desativar, considerar evoluir esse contrato para
receber o estado desejado, revisando os clientes existentes.

Se for exigida exclusão, avaliar uma sexta rota, `DELETE /units/{unitId}`,
somente após definir bloqueios por usuários, eventos, histórico e demais
vínculos. Esta rota não faz parte das cinco propostas principais.

## Confirmação de senha: módulo auth

Não há necessidade automática de criar um endpoint próprio para conferir senha.
Uma opção é usar a reautenticação do Firebase no frontend. Se a confirmação
recente for requisito de segurança destas mutações, a API também precisa
validar essa condição no token: o filtro atual só propaga UID, email e perfil
em `AuthenticatedUser` e não impõe uma janela de autenticação recente.
É uma regra proposta para o Astro, não uma exigência automática do Firebase
para editar dados da empresa.

Referências oficiais: [reautenticação no cliente](https://firebase.google.com/docs/auth/web/manage-users#re-authenticate_a_user)
e [claims de ID tokens, incluindo auth_time](https://firebase.google.com/docs/auth/admin/verify-id-tokens).

## Base de código conferida

- `workspace/controller/WorkspaceController.java`: cadastro inicial e fila de email de gestor.
- `workspace/service/WorkspaceService.java` e `WorkspaceRegistrationTransactionalService.java`: criação inicial.
- `workspace/service/SpreadsheetImportService.java`: leitura e validação da planilha inicial.
- `workspace/service/PreRegistrationQueueService.java`: fila após commit.
- `workspace/model/Workspace.java` e `workspace/repository/WorkspaceRepository.java`.
- `unit/controller/UnitController.java`, `unit/service/UnitService.java` e `docs/api-units.md`.
- `unit/model/UnitAddress.java`, `unit/mapper/UnitMapper.java`, `unit/repository/UnitRepository.java` e `unit/dto/response/UnitResponse.java`.
- `user/controller/CollaboratorController.java`, `user/service/CollaboratorService.java` e `user/service/UserBatchService.java`.
- `user/dto/response/UserProfileResponse.java`.
- `auth/security/FirebaseAuthenticationFilter.java` e `auth/security/AuthenticatedUser.java`.
- `common/handler/GlobalExceptionHandler.java` e `workspace/exception/SpreadsheetImportException.java`.

Não foram executados testes de mutação contra a conta, nem testes automatizados,
pois esta entrega é uma análise e não altera a implementação.
