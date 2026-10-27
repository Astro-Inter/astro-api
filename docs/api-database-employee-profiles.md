# Perfis de funcionários e integração com o banco (SCRUM-635 / SCRUM-640)

As Homes usam `fn_carrega_home_colaborador(TEXT)`, `fn_carrega_home_gestor(TEXT)` e
`fn_carrega_workspace(TEXT)`. O banco deve conter as definições atualizadas da
PR Astro-Inter/banco-de-dados#47 antes da publicação desta API.

A Home do colaborador admite COLABORADOR, GESTOR e GESTOR_WORKSPACE. A Home do
gestor continua restrita a GESTOR e GESTOR_WORKSPACE. Os participantes dos três
perfis entram nas métricas calculadas pelo banco. A Home do workspace consome
as estatísticas do banco sem recalcular ou filtrar perfis em Java.

Conformidades podem pertencer aos três perfis. A listagem, os detalhes, a edição,
a aplicabilidade e a importação mantêm workspace e unidades autorizadas. Novas
conformidades e reativações exigem usuário ATIVO; a consulta/edição de histórico
mantém as regras de status e NRs revogadas existentes. ADMIN não é destinatário
nem ganha acesso aos endpoints. Na gestão cadastral, o gestor do workspace pode
editar os três perfis no escopo. O gestor comum consulta todos, mas não edita
outros gestores nem altera seu status. O campo `canManage` reflete essa regra.

A listagem e os detalhes de colaboradores incluem os três perfis com a versão
atualizada de `05_vw_colaboradores.sql`. `excludeSelf=true` oculta o solicitante
apenas na listagem que enviar esse parâmetro; conformidades e seus seletores
mantêm a própria conta. `03_vw_cargos.sql` também conta os três perfis ativos.

`collaboratorCount` nas respostas de criação/edição de unidades conta somente
usuários ATIVOS dos três perfis vinculados à mesma unidade. A listagem segue
`vw_unidades`. A estatística do workspace também exige unidade ativa, conforme
a regra da view específica. Os campos JSON permanecem iguais.

O envio de conclusão em `POST /events/{eventId}/completion` já permite os três
perfis. Permanecem as validações de participação própria, usuário ativo, evento,
prazo, modo de conclusão e evidências. ADMIN continua excluído.

Validação automatizada: `./mvnw --batch-mode --no-transfer-progress verify`.
As credenciais de contas reais não fazem parte dos testes nem dos arquivos do projeto.

A view `vw_colaboradores` deve expor `tipo` como última coluna. A listagem e os detalhes leem esse campo diretamente para calcular `canManage`, sem uma nova junção à tabela `usuario`. Aplicar essa versão da view antes de publicar a API; os filtros de workspace e `fn_seleciona_unidades` permanecem nas consultas.
