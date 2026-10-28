# Conformidades de unidades — SCRUM-657

Ambiente experimental local. O schema oficial é instalado do zero; nenhum script desta tarefa migra o banco publicado.

A tabela conformidade admite tipo COLABORADOR ou UNIDADE e exatamente um destinatário. Os registros atuais continuam como COLABORADOR por padrão. A view de colaboradores preserva o contrato; vw_conformidade_unidade seleciona um representante por unidade/NR pelos mesmos critérios de aplicabilidade, validade e ID.

Rotas novas: GET/POST /conformities/units; GET/PUT /conformities/units/{id}; PATCH /conformities/units/{id}/applicability. Exigem Firebase, GESTOR/GESTOR_WORKSPACE ativo, workspace e fn_seleciona_unidades. POST recebe unitId, nrId, expiresAt opcional. PUT exige nrId e expiresAt presente, podendo ser null. A resposta contém id, type UNIDADE, unitId, unitName, nrId, nrTitle, expiresAt, origin, applicable, formResponseId e status. Erros usam ApiResult. Importação Excel permanece exclusiva de colaboradores.

Aprovar formulário concluído com NR gera histórico de unidade, origem FORMULARIO. A unidade é a indicada no formulário; na ausência dela, a unidade do destinatário, consultada na aprovação. A validade é a data da resposta no fuso America/Sao_Paulo mais tempo_reciclagem_mes, ajustando finais de mês. Sem NR ou reprovado não gera registro. Uma aprovação posterior acrescenta histórico, sem sobrescrever evidências anteriores. A view escolhe o representante. Alterar a validade manualmente é permitido; trocar a NR vinculada a formulário é recusado.

MongoDB e PostgreSQL não compartilham transação: primeiro é persistida a aprovação condicional no Mongo; depois é criada a conformidade no PostgreSQL. Se a segunda etapa falhar, a aprovação permanece salva e a requisição retorna erro. Repetir POST /forms/{id}/review com status approved reconcilia a conformidade sem duplicar ou alterar a decisão; isso também pode ser usado por um operador autorizado para recuperar falhas. O vínculo único por formulário e o bloqueio de escrita tornam essa reconciliação idempotente. Não existe reconciliação automática em segundo plano nesta versão.

Nenhum endpoint novo torna a NR inteira conforme apenas por preencher respostas; a aprovação existente é o critério funcional definido para este protótipo.

As rotas de colaboradores usam /conformities/collaborators, incluindo /import, /{id} e /{id}/applicability. As rotas de unidades usam /conformities/units. O caminho antigo /conformities foi substituído neste protótipo local.
