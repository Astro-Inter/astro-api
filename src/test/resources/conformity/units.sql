CREATE OR REPLACE FUNCTION fn_seleciona_unidades(
    p_firebase_uid TEXT
)
RETURNS SETOF vw_unidades
LANGUAGE sql
STABLE
AS $$
    SELECT vu.*
    FROM vw_unidades vu
    INNER JOIN usuario solicitante
        ON solicitante.firebase_uid = p_firebase_uid
    INNER JOIN unidade unidade_gestor
        ON unidade_gestor.id_unidade = solicitante.unidade_id
    WHERE NULLIF(BTRIM(p_firebase_uid), '') IS NOT NULL
      AND solicitante.tipo IN ('GESTOR', 'GESTOR_WORKSPACE')
      AND solicitante.status = 'ATIVO'
      AND vu.id_workspace = unidade_gestor.workspace_id
    ORDER BY vu.nome, vu.id_unidade;
$$;

COMMENT ON FUNCTION fn_seleciona_unidades(TEXT) IS
'Retorna as unidades do workspace associado à unidade do gestor ativo identificado pelo Firebase UID, incluindo seus endereços e a quantidade de colaboradores ativos. Retorna zero linhas quando o UID é nulo, vazio, inexistente, inativo ou não pertence a um gestor.';
