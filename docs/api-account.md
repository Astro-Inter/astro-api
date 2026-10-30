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
  "data": { "name": "Maria Rosa", "email": "maria@example.com", "profilePhotoUrl": null },
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
  "name": "Maria Rosa",
  "cpf": null,
  "email": "maria@example.com",
  "roleId": 20,
  "roleName": "Engenheira",
  "unitId": 10,
  "unitName": "Osasco",
  "workModel": "PRESENCIAL",
  "profilePhotoUrl": null
}
```

`workModel` usa `HIBRIDO`, `REMOTO` ou `PRESENCIAL`, como no mobile; pode ser
`null` em cadastros antigos. Nome, CPF, cargo/roleId e foto também podem ser
`null` quando ausentes. IDs e nomes vêm dos vínculos reais. Vínculos atuais
inativos continuam visíveis; cargo de outro workspace bloqueia o acesso.
Não retorna senha, NRs, tokens, status, workspaceId nem a entidade completa.

O CPF é armazenado em `usuario.cpf` nos colaboradores importados e preservado
na promoção a GESTOR. No cadastro inicial de GESTOR_WORKSPACE, CPF e modalidade
também são importados da linha do gestor quando preenchidos na planilha.
Os campos continuam opcionais para o gestor e são validados antes da criação
da conta. Consulte [Cadastro de workspace](api-workspace-registration.md).
Cadastros anteriores não recebem preenchimento retroativo; a edição da conta
permite informar os dados reais ausentes.

## PUT /user/me/account-details (SCRUM-571)

Substitui o conjunto completo dos **seis** campos editáveis. Todos são
obrigatórios e não nulos; omissão, campo desconhecido e valor inválido retornam
400, antes de qualquer escrita. Exemplo de request:

```json
{
  "name": "Maria Rosa",
  "cpf": "52998224725",
  "email": "maria@example.com",
  "roleId": 20,
  "unitId": 10,
  "workModel": "PRESENCIAL"
}
```

Nome: trim, 2–255 caracteres, não vazio. CPF: validação de dígitos verificadores
e 11 dígitos, com remoção de pontos/hífen antes de validar, conforme os DTOs
existentes. E-mail: trim, minúsculas, formato válido, máximo de 255 caracteres.
IDs: inteiros positivos. Modalidade: `HIBRIDO`, `REMOTO` ou `PRESENCIAL`.
Quando o GET retornar CPF/modalidade ausentes, é necessário informar os valores
reais para salvar. Senha, hash, foto, status, tipo de conta, workspace e IDs de
usuário não fazem parte do request; campos adicionais são rejeitados.

HTTP 200 retorna os mesmos nove campos de detalhes, atualizados, em `ApiResult`.
A foto continua sendo enviada separadamente a `PUT /user/me/profile-photo`
(multipart, campo `file`). Opções existentes: `GET /roles` e `GET /units`.

O request usa os nomes Java em inglês, sem `@JsonProperty`: `name`, `cpf`,
`email`, `roleId`, `unitId` e `workModel`. O frontend deve enviar esses nomes;
`nome` e `modalidade` são campos desconhecidos no PUT e retornam 400.
As respostas GET/PUT mantêm os nomes documentados acima (`name`, `roleName`,
`unitName`, `workModel`).

### Autorização dos vínculos

- GESTOR pode editar nome, CPF, e-mail e modalidade, mantendo cargo/unidade.
  Envia os IDs atuais no PUT completo; tentar mudá-los retorna 403. O cargo
  também interfere nas NRs, por isso sua autoalteração não é autorizada aqui.
- GESTOR_WORKSPACE pode escolher cargo e unidade existentes dentro do workspace
  atual. Novos vínculos precisam estar ativos; o status é lido com bloqueio
  compartilhado para serializar a validação com alterações concorrentes.
- Vínculos inativos já existentes podem ser mantidos, como na edição de gestores.
  Cargo/unidade inexistentes ou externos retornam 404 sem revelar outro workspace.
- A conta é bloqueada para escrita pelo UID do token, e o tipo/status são
  revalidados. A lista do dropdown não concede autorização. Tipo, status,
  workspace, UID, data de criação e foto não são campos editáveis; contas
  antigas sem cargo exigem correção administrativa se forem GESTOR.

### E-mail, consistência e confirmação

Reutiliza `FirebaseIdentityService`: e-mail inalterado não chama o Firebase;
alteração exige que o endereço anterior no Firebase corresponda ao banco
(divergência retorna 409). Verifica duplicidade de CPF/e-mail excluindo somente
a própria conta; restrições de integridade do banco também retornam 409.

Após o flush, atualiza a identidade Firebase existente pelo UID, sem criar
outra conta, e define `emailVerified=false`, conforme o fluxo administrativo já
existente. Só responde 200 depois da sincronização e do commit PostgreSQL.
Duplicidade explícita do Firebase retorna 409 e reverte o banco. Falha remota
incerta ou falha no commit reverte o banco e tenta restaurar e-mail e estado de
verificação anteriores no Firebase. Se a compensação falhar, tenta desativar a
conta em transação separada para conter o acesso e retorna erro. Se a contenção
também falhar, registra a falha para reconciliação operacional; nenhuma dessas
falhas é apresentada como sucesso. Banco e Firebase não têm transação distribuída.

O projeto não exige `emailVerified` nem reautenticação recente para esta edição
e não possui envio de confirmação do novo endereço neste fluxo. O 200 confirma
**gravação e sincronização**, não propriedade do novo e-mail. A extensão proposta
para o frontend é recarregar a identidade Firebase e enviar a verificação pelo
SDK do cliente; eventual exigência de confirmar antes de efetivar a troca precisa
de um fluxo de e-mail pendente e validação backend próprios. Esta entrega não
inventa esse fluxo nem novos endpoints e não utiliza senha confirmada só no
navegador como autorização. Após mudar o e-mail, o cliente deve recarregar a
identidade/token ou entrar novamente para atualizar as informações da sessão.

### Validação da entrega

Testes de controller com o filtro Firebase real e dependências externas simuladas
cobrem ambos os perfis, token ausente/inválido, campos exatos, nulos, PUT completo,
validações e erros padronizados. Testes de serviço cobrem isolamento, permissões,
vínculos ativos/inativos, conflitos, falhas de flush/commit/Firebase e compensação.
`ProfileServiceTest`, `UserControllerTest` e o teste de contrato em
`AccountSecurityTest` preservam a consulta mobile. Não exigem credenciais reais.

`AccountPostgresTest` valida consultas, atualização, conflitos, vínculos e rollback
com PostgreSQL real. Reutiliza a fixture e o banco descartável do CI, dentro de
um schema aleatório próprio. Só executa com `MANAGER_TEST_DATABASE_URL` apontando
para `jdbc:postgresql://localhost:<porta>/astro525` (ou `127.0.0.1`); sem essa
variável fica ignorado. Usa as variáveis de usuário/senha do teste de gestores.
Execute `mvn --batch-mode --no-transfer-progress verify` para testes e build.

## Erros

A configuração de segurança existente responde **403** para token ausente ou
inválido (antes de entrar no controller, sem garantia do envelope ApiResult).
Perfil não permitido ou conta inativa também não obtêm acesso. Erros tratados
pelo controller advice usam `ApiResult` com `success: false`, `data: null`,
`message`, `errors` e `path`: 403 para acesso negado, 404 para usuário não
encontrado, 500 para falha interna/foto e 503 para indisponibilidade dos dados.
O filtro pode rejeitar um UID sem conta antes de alcançar o 404 do serviço.
