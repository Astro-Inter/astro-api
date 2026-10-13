# Home WEB

`GET /home-workspace` consulta os indicadores do workspace do usuário autenticado.
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
  "path": "/home-workspace"
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
