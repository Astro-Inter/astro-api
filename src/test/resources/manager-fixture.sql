-- Existing database contracts, recreated only in the dedicated test database.
CREATE TABLE workspace (id_workspace BIGINT PRIMARY KEY, nome VARCHAR(255), cnpj VARCHAR(14));
CREATE TABLE cargo (id_cargo BIGINT PRIMARY KEY, workspace_id BIGINT REFERENCES workspace,
    nome VARCHAR(255), ativo BOOLEAN);
CREATE TABLE unidade (id_unidade BIGINT PRIMARY KEY, workspace_id BIGINT REFERENCES workspace,
    nome VARCHAR(255), ativo BOOLEAN);
CREATE TABLE usuario (id_usuario BIGINT PRIMARY KEY, nome VARCHAR(255), email VARCHAR(255),
    firebase_uid VARCHAR(128), cpf CHAR(11), tipo VARCHAR(50), modalidade VARCHAR(50),
    status VARCHAR(50), criado_em TIMESTAMP, cargo_id BIGINT REFERENCES cargo,
    unidade_id BIGINT REFERENCES unidade,
    CONSTRAINT uq_usuario_cpf UNIQUE(cpf), CONSTRAINT uq_usuario_email UNIQUE(email));
CREATE VIEW vw_gestores AS
SELECT u.id_usuario, u.nome, u.email, u.cpf, u.modalidade, u.status,
       u.cargo_id, c.nome AS cargo, u.unidade_id, un.nome AS unidade, un.workspace_id
FROM usuario u INNER JOIN cargo c ON c.id_cargo = u.cargo_id
INNER JOIN unidade un ON un.id_unidade = u.unidade_id WHERE u.tipo = 'GESTOR';
