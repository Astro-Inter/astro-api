# Cadastro inicial de workspace

Os endereços das unidades são geocodificados pelo backend via Geoapify e gravados
em `unidade_endereco.geolocalizacao`. Falhas na busca não impedem o cadastro.
Configuração, persistência e nova tentativa: [geolocalização de unidades](unit-geolocation.md).

`POST /register-workspace` é público e recebe multipart com `data` (JSON de
`CreateWorkspaceRequest`) e `file` (planilha XLS/XLSX de até 5 MiB). A primeira
aba usa as colunas `nome`, `cpf`, `email`, `unidade`, `cargo` e `modalidade`.
Deve haver exatamente uma linha com o e-mail do gestor informado no cadastro.
Nome, cargo e unidade do gestor continuam obrigatórios.

CPF e modalidade do gestor continuam opcionais. Quando preenchidos:

- O CPF segue a normalização do cadastro inicial (somente dígitos), deve ter
  onze dígitos e verificadores válidos. Um CPF existente no banco ou repetido
  entre gestor e colaborador rejeita a planilha.
- A modalidade aceita `PRESENCIAL`, `REMOTO` e `HIBRIDO`, ignorando espaços
  externos, acentos e diferenças entre maiúsculas e minúsculas.
- Os valores são salvos em `usuario.cpf` e `usuario.modalidade`, junto com nome,
  e-mail, cargo e unidade. Campo opcional vazio é salvo como `null`.

O gestor mantém `GESTOR_WORKSPACE`, status `ATIVO` e o UID criado no Firebase.
Os colaboradores mantêm `COLABORADOR` e `PRE_CADASTRADO`, em lote separado.
O gestor não entra no lote nem nos e-mails de ativação dos colaboradores.

CPF ou modalidade inválidos do gestor retornam `422` no envelope `ApiResult`,
com `data` contendo `row`, `field` (`cpf` ou `modalidade`) e `message`.
O campo `errors` mantém a orientação para corrigir as linhas e reenviar.
As linhas consideram o cabeçalho: a primeira linha de dados é a 2. Toda a
validação ocorre antes de criar a conta Firebase ou persistir o workspace.
Sucesso retorna `201`, com `data.workspaceId` e `data.importErrors: []`.
Conflitos estruturais de CNPJ/e-mail continuam retornando `409`.

A persistência relacional continua na transação do cadastro. Se ela falhar após
a criação Firebase, o fluxo existente solicita a compensação dessa identidade.
O formato da requisição e as permissões permanecem iguais. A correção vale
para novos cadastros; não preenche automaticamente gestores existentes.

`WorkspaceRegistrationServiceTest` cobre arquivos XLS/XLSX, as três modalidades,
normalização do CPF, campos opcionais, entradas inválidas, CPF existente,
duplicação com colaborador e preservação do lote e da fila de colaboradores.
Os testes usam planilhas fictícias e substituem os serviços externos por mocks.
