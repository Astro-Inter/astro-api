CREATE OR REPLACE VIEW vw_conformidades AS
SELECT
    cf.id_conformidade,
    cf.usuario_id,
    u.nome,
    u.email,
    u.status AS status_colaborador,
    u.cargo_id,
    u.unidade_id,
    un.workspace_id,
    cf.nr_id,
    nr.titulo AS titulo_nr,
    cf.aplicavel,
    cf.data_validade,
    cf.origem,
    cf.conclusao_evento_id,
    CASE
        WHEN cf.data_validade IS NULL THEN 'Sem validade'
        WHEN cf.data_validade < CURRENT_DATE THEN 'Vencida'
        ELSE 'Válida'
    END AS status
FROM (
    SELECT
        cf.id_conformidade,
        cf.usuario_id,
        cf.nr_id,
        cf.aplicavel,
        cf.data_validade,
        cf.origem,
        cf.conclusao_evento_id,
        ROW_NUMBER() OVER (
            PARTITION BY cf.usuario_id, cf.nr_id
            ORDER BY
                cf.aplicavel DESC,
                cf.data_validade DESC NULLS LAST,
                cf.id_conformidade DESC
        ) AS ordem
    FROM conformidade cf
) cf
INNER JOIN usuario u ON u.id_usuario = cf.usuario_id
INNER JOIN cargo c ON c.id_cargo = u.cargo_id
INNER JOIN unidade un ON un.id_unidade = u.unidade_id
INNER JOIN nr_catalogo nr ON nr.codigo_nr = cf.nr_id
WHERE cf.ordem = 1
  AND u.tipo = 'COLABORADOR';

COMMENT ON VIEW vw_conformidades IS
'Reúne os dados de conformidades para listagem e edição, com uma linha por colaborador e NR já registrados em conformidade. Prioriza registros aplicáveis, depois a maior validade (nulos por último) e, em empate, o maior ID. ID, aplicabilidade, validade, origem e conclusão pertencem ao mesmo registro selecionado; editar pelo ID afeta somente esse registro, sem alterar o restante do histórico. Quando todos os registros do par são não aplicáveis, mantém uma linha não aplicável. Mantém colaboradores de todos os status, inclusive com cargo ou unidade inativos, e NRs revogadas. Calcula Vencida para validade anterior a hoje, Válida para hoje ou depois e Sem validade para data nula, independentemente da aplicabilidade. Não infere requisitos ausentes a partir de cargo_nr ou unidade_nr, nem expõe senha ou Firebase UID. O backend deve restringir todas as consultas pelo workspace e pelas unidades permitidas e decidir quais registros não aplicáveis e NRs revogadas serão exibidos. Datas seguem o fuso da sessão PostgreSQL.';
