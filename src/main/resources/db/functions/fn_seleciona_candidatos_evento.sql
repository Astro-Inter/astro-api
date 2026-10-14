-- Install before deploying the API revision that calls this function.
-- Read-only lookup: the caller keeps table privileges and existing RLS policies.
CREATE OR REPLACE FUNCTION fn_seleciona_candidatos_evento(
    p_firebase_uid TEXT,
    p_nr_id INTEGER DEFAULT NULL
)
RETURNS TABLE (
    id_usuario BIGINT,
    nome VARCHAR(255),
    email VARCHAR(255),
    cargo_id BIGINT,
    cargo_nome VARCHAR(255),
    unidade_id BIGINT,
    unidade_nome VARCHAR(255),
    modalidade VARCHAR(50)
)
LANGUAGE sql
STABLE
SECURITY INVOKER
AS $$
    SELECT candidato.id_usuario, candidato.nome, candidato.email,
           cargo.id_cargo, cargo.nome,
           unidade.id_unidade, unidade.nome, candidato.modalidade
    FROM usuario solicitante
    INNER JOIN unidade origem ON origem.id_unidade = solicitante.unidade_id
    INNER JOIN unidade unidade ON unidade.workspace_id = origem.workspace_id
    INNER JOIN usuario candidato ON candidato.unidade_id = unidade.id_unidade
    LEFT JOIN cargo cargo ON cargo.id_cargo = candidato.cargo_id
    WHERE NULLIF(BTRIM(p_firebase_uid), '') IS NOT NULL
      AND solicitante.firebase_uid = p_firebase_uid
      AND solicitante.tipo IN ('GESTOR', 'GESTOR_WORKSPACE')
      AND solicitante.status = 'ATIVO'
      AND candidato.status = 'ATIVO'
      AND candidato.id_usuario <> solicitante.id_usuario
      AND (p_nr_id IS NULL OR EXISTS (
          SELECT 1 FROM cargo_nr vinculo
          WHERE vinculo.cargo_id = candidato.cargo_id AND vinculo.nr_id = p_nr_id
      ))
    ORDER BY candidato.nome, candidato.id_usuario;
$$;

COMMENT ON FUNCTION fn_seleciona_candidatos_evento(TEXT, INTEGER) IS
'Lista candidatos ativos para eventos no workspace do gestor autenticado, excluindo o próprio solicitante. Aceita NR opcional para filtrar o vínculo do cargo, preserva outros gestores elegíveis e retorna uma linha por usuário com cargo, unidade e modalidade.';
