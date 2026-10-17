-- Existing event constraints from banco-de-dados, applied only in an isolated test schema.
CREATE TABLE nr_catalogo (codigo_nr INTEGER PRIMARY KEY, titulo VARCHAR(255), tempo_reciclagem_mes INTEGER, revogada BOOLEAN);
CREATE TABLE cargo_nr (cargo_id BIGINT REFERENCES cargo, nr_id INTEGER REFERENCES nr_catalogo, PRIMARY KEY(cargo_id,nr_id));
CREATE TABLE evento (
    id_evento BIGSERIAL PRIMARY KEY, gestor_id BIGINT NOT NULL REFERENCES usuario,
    nr_id INTEGER REFERENCES nr_catalogo, titulo VARCHAR(255) NOT NULL CHECK(char_length(btrim(titulo)) >= 3),
    descricao TEXT NOT NULL CHECK(char_length(btrim(descricao)) >= 3),
    link_externo VARCHAR(2048) CHECK(link_externo IS NULL OR link_externo ~* '^https?://[^[:space:]]+$'),
    modo_conclusao VARCHAR(50) NOT NULL CHECK(modo_conclusao IN ('COLABORADOR','GESTOR','LISTA_PRESENCA')),
    evidencia_obrigatoria BOOLEAN NOT NULL, status VARCHAR(50) NOT NULL CHECK(status IN ('ATIVO','CANCELADO','CONCLUIDO')),
    data_cancelamento TIMESTAMP, motivo_cancelamento TEXT,
    CHECK((status='CANCELADO' AND data_cancelamento IS NOT NULL AND motivo_cancelamento IS NOT NULL AND char_length(btrim(motivo_cancelamento)) >= 3)
       OR (status<>'CANCELADO' AND data_cancelamento IS NULL AND motivo_cancelamento IS NULL))
);
CREATE TABLE turma (
    id_turma BIGSERIAL PRIMARY KEY, evento_id BIGINT NOT NULL REFERENCES evento,
    nome VARCHAR(255) NOT NULL CHECK(char_length(btrim(nome)) >= 1),
    data_inicial TIMESTAMP NOT NULL, data_termino TIMESTAMP NOT NULL CHECK(data_termino > data_inicial),
    CONSTRAINT uq_turma_evento_nome UNIQUE(evento_id,nome)
);
CREATE TABLE turma_funcionario (
    id_turma_funcionario BIGSERIAL PRIMARY KEY, turma_id BIGINT NOT NULL REFERENCES turma,
    usuario_id BIGINT NOT NULL REFERENCES usuario, UNIQUE(turma_id,usuario_id)
);
CREATE TABLE conclusao_evento (id_conclusao_evento BIGSERIAL PRIMARY KEY,
    turma_funcionario_id BIGINT NOT NULL UNIQUE REFERENCES turma_funcionario, status VARCHAR(50));
CREATE TABLE evidencia (id_evidencia BIGSERIAL PRIMARY KEY, conclusao_evento_id BIGINT NOT NULL REFERENCES conclusao_evento,
    caminho_objeto VARCHAR(2048));
