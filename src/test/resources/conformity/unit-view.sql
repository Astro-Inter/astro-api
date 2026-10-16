CREATE OR REPLACE VIEW vw_unidades AS
SELECT
    un.id_unidade,
    un.workspace_id AS id_workspace,
    un.nome,
    COUNT(DISTINCT u.id_usuario) FILTER (
        WHERE u.tipo = 'COLABORADOR'
          AND u.status = 'ATIVO'
    ) AS quantidade_colaboradores,
    CASE
        WHEN un.ativo THEN 'Ativo'
        ELSE 'Inativo'
    END AS status,
    endereco.cep,
    endereco.rua,
    endereco.cidade,
    endereco.bairro,
    endereco.estado,
    endereco.complemento
FROM unidade un
LEFT JOIN usuario u
    ON u.unidade_id = un.id_unidade
LEFT JOIN unidade_endereco endereco
    ON endereco.unidade_id = un.id_unidade
GROUP BY
    un.id_unidade,
    un.workspace_id,
    un.nome,
    un.ativo,
    endereco.cep,
    endereco.rua,
    endereco.cidade,
    endereco.bairro,
    endereco.estado,
    endereco.complemento;

COMMENT ON VIEW vw_unidades IS
'Lista unidades com workspace, nome, status, endereço e quantidade de colaboradores ativos vinculados. Unidades sem colaboradores também aparecem com quantidade zero.';
