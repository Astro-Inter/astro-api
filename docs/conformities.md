# Conformidades — SCRUM-536

`PUT /conformities/{id}` exige `nrId` e a presença de `expiresAt`. Campo ausente causa 400; `expiresAt: null` remove a validade. O colaborador, o ID, a origem, a aplicabilidade e a conclusão permanecem. Trocar a NR de um aplicável exige ausência de outro aplicável na NR de destino. Uma NR nova não pode estar revogada; manter a NR histórica é permitido. Edição de histórico de colaborador inativo é permitida. A NR deve corresponder à conclusão vinculada; `conclusao_evento.data_validade` não é alterada. A resposta usa o ID editado, mesmo quando outro registro continua representando o par na view.

O módulo Java é `com.astro.api.conformity`. A renomeação atualiza imports e testes; a rota pública `/nrs` e seus campos permanecem compatíveis. `/collaborators` retorna os colaboradores autorizados e `/nrs` retorna código/título das NRs para seleção. O catálogo inclui NRs revogadas; uma nova vinculação revogada é rejeitada pela API.

Todos os endpoints exigem autenticação Firebase validada e perfil GESTOR ou GESTOR_WORKSPACE ativo. O workspace vem do usuário autenticado e as unidades vêm de `fn_seleciona_unidades`. Na versão oficial consultada, ambos os perfis acessam todas as unidades de seu workspace. Identificadores enviados para outro workspace recebem 404. As respostas usam `ApiResult` e DTOs sem senha ou Firebase UID.

`GET /conformities` consulta `vw_conformidades` e retorna somente aplicáveis, ordenados por nome, colaborador, NR e ID. A view escolhe um registro do par: aplicável primeiro, maior validade com nulos por último e maior ID em empate. Origem, validade, ID e conclusão são do mesmo registro; origens desconhecidas são preservadas. O histórico autorizado inclui colaboradores inativos e NRs revogadas.

`GET /conformities/{id}` consulta a tabela e os relacionamentos, inclusive para registros não aplicáveis ou não selecionados na view. Status usa `CURRENT_DATE` do PostgreSQL: antes de hoje = Vencida; hoje ou depois = Válida; nulo = Sem validade. Nenhum status é persistido.

`POST /conformities` recebe `collaboratorId`, `nrId` e `expiresAt` (ISO `yyyy-MM-dd`, opcional ou nulo). Exige colaborador ATIVO e NR não revogada. Cria MANUAL/aplicável/sem conclusão. Qualquer aplicável do par causa 409; registros não aplicáveis são preservados.

As escritas usam transação e `LOCK TABLE conformidade IN SHARE ROW EXCLUSIVE MODE`, antes dos bloqueios de colaborador e NR. Esse bloqueio conservador serializa escritores e conflita também com DML direto, sem impedir leituras ou exigir novos objetos SQL. A verificação do conflito acontece depois do bloqueio, com READ COMMITTED. O custo é serializar gravações de diferentes pares; a importação deve manter uma transação curta por linha. Não há fluxo de conclusão de eventos que escreva conformidades na versão desta API; futuros escritores devem respeitar a mesma ordem de bloqueios. Conclusões podem acrescentar histórico depois de uma operação administrativa; não existe UNIQUE por colaborador/NR.

Os objetos de banco da SCRUM-533/534 já foram incorporados em `Astro-Inter/banco-de-dados` no commit `72548b1` (PR #34). Isso confirma o código disponível, não sua implantação no banco publicado. Esta entrega não cria objetos SQL de produção. `src/test/resources/conformity` contém cópias dos scripts oficiais exclusivamente para um schema de teste gerado.

Para executar os testes PostgreSQL, use um banco descartável `scrum536` em localhost e configure `CONFORMITY_TEST_JDBC_URL=jdbc:postgresql://127.0.0.1:PORT/scrum536`. Os testes criam um schema exclusivo, aplicam os scripts oficiais e removem somente esse schema. O CI fornece esse banco isolado.
