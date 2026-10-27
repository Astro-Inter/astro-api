# Home WEB

`GET /home/workspace` consulta os indicadores do workspace do usuário autenticado.
Envie `Authorization: Bearer <Firebase ID token>`. Não há body nem parâmetros de
consulta obrigatórios: o Firebase UID vem exclusivamente da autenticação validada.
Somente `GESTOR` e `GESTOR_WORKSPACE` podem acessar a rota.

Exemplo de resposta HTTP 200:

```json
{
  "success": true,
  "message": "Dados da Home retornados com sucesso",
  "data": {
    "nome_usuario": "Kirk Hammett",
    "profilePhotoUrl": "https://arquivos.example.com/foto-assinada",
    "estatisticas": {
      "quantidade_unidades": 4,
      "quantidade_colaboradores": 248,
      "quantidade_requisitos": 100,
      "quantidade_conformes": 85,
      "quantidade_proximos_vencimento": 7,
      "quantidade_vencidos": 5,
      "quantidade_pendentes": 3,
      "percentual_conformidade_geral": 92.00,
      "data_referencia": "2026-10-12"
    }
  },
  "errors": null,
  "path": "/home/workspace"
}
```

Use `nome_usuario` na saudação, as quantidades de unidades e colaboradores nos
cards e os indicadores de conformidade no gráfico. Os valores acima são ilustrativos.
As quatro categorias de requisitos são exclusivas e somam `quantidade_requisitos`.
Pendentes também entram no denominador; o front deve considerá-los ao representar
o total no gráfico. O percentual geral inclui conformes e próximos do vencimento.
A janela de próximos do vencimento vai de hoje até 30 dias à frente, inclusive.
Sem requisitos, o percentual é zero. A data de referência segue a sessão PostgreSQL.

O banco conta unidades ativas e colaboradores ativos dessas unidades. Avalia
conformidades registradas como aplicáveis, de NRs não revogadas, consolidadas pela
maior validade por colaborador/NR. Não infere requisitos ausentes a partir de cargos.

HTTP 403 indica perfil sem permissão ou retorno SQL NULL da função (usuário
inexistente/inativo, unidade inativa ou ausência de acesso ao workspace). Falhas de
conexão seguem o HTTP 503 padrão da API; JSON inválido do banco retorna HTTP 500.

Antes de disponibilizar a rota, instale os objetos da
[PR 29 do banco](https://github.com/Astro-Inter/banco-de-dados/pull/29): primeiro
`vw_home_estatisticas_gerais`, depois `fn_carrega_workspace(TEXT)`. O repository
consulta a função com parâmetro nomeado e converte JSONB para texto; o service
desserializa para DTOs com identificadores Java em inglês e mapeamento explícito
dos campos JSON em português.


## Foto da própria conta

`GET /home/workspace` inclui `profilePhotoUrl` no mesmo envelope dos
indicadores. O campo sempre é serializado, com URL temporária ou `null`.
Sem foto, sem usuário na consulta auxiliar ou em falha da consulta/assinatura,
os indicadores continuam sendo retornados e a foto vem como `null`.
O web não precisa consultar o resumo da conta ao abrir a home.

A identidade vem exclusivamente do principal Firebase. A função
`fn_carrega_workspace(TEXT)` valida o acesso antes da consulta opcional da foto;
UIDs e IDs enviados por query string não são utilizados. A rota restringe o
acesso a GESTOR e GESTOR_WORKSPACE por segurança de método.

WorkspaceHomeDatabaseResponse mantém o contrato da função PostgreSQL.
WorkspaceHomeMapper monta WorkspaceHomeResponse com o campo adicional,
sem alterar banco nem expor entidades. WorkspaceHomeProfileService reutiliza
HomeRepository.findByFirebaseUid (projeção de ID) e UserAvatarService.
A consulta derivada existente é suficiente, pois busca somente o ID da
identidade autenticada; não há necessidade de outra query SQL.

A consulta opcional suspende a transação dos indicadores com NOT_SUPPORTED.
Isso evita que uma falha de banco na foto marque a transação da home para
rollback. Erros reais dos indicadores continuam seguindo os códigos HTTP
existentes. O log de falha da foto não inclui UID, URL, token ou dados pessoais.

Validação: testes de service, MockMvc, segurança de método e isolamento
transacional cobrem foto presente, null, falha de armazenamento/banco,
preservação dos indicadores e autorização. A execução completa usa JDK 21
com `mvn --batch-mode --no-transfer-progress verify`; integrações que exigem
serviços externos podem ser ignoradas quando suas variáveis não estão definidas.
