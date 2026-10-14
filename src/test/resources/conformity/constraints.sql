ALTER TABLE workspace
    ALTER COLUMN id_workspace SET NOT NULL, 
    ALTER COLUMN nome SET NOT NULL,         
    ALTER COLUMN cnpj SET NOT NULL;        

ALTER TABLE workspace
    ADD CONSTRAINT pk_workspace PRIMARY KEY (id_workspace),
    ADD CONSTRAINT uq_workspace_cnpj UNIQUE (cnpj),
    ADD CONSTRAINT ck_workspace_nome CHECK (CHAR_LENGTH(BTRIM(nome)) >= 2),
    ADD CONSTRAINT ck_workspace_cnpj CHECK (cnpj ~ '^[0-9]{14}$');

ALTER TABLE conta
    ALTER COLUMN email SET NOT NULL;

ALTER TABLE conta
    ADD CONSTRAINT uq_conta_firebase_uid UNIQUE (firebase_uid),
    ADD CONSTRAINT ck_conta_nome CHECK (CHAR_LENGTH(BTRIM(nome)) >= 2),
    ADD CONSTRAINT ck_conta_email CHECK (email ~* '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$');

ALTER TABLE cargo
    ALTER COLUMN id_cargo SET NOT NULL,
    ALTER COLUMN workspace_id SET NOT NULL, 
    ALTER COLUMN nome SET NOT NULL,
    ALTER COLUMN ativo SET DEFAULT TRUE;

ALTER TABLE cargo
    ADD CONSTRAINT pk_cargo PRIMARY KEY (id_cargo),
    ADD CONSTRAINT uq_cargo_workspace_nome UNIQUE (workspace_id, nome), -- O nome não se repete dentro do mesmo workspace.
    ADD CONSTRAINT ck_cargo_nome CHECK (CHAR_LENGTH(BTRIM(nome)) >= 2);


ALTER TABLE unidade
    ALTER COLUMN id_unidade SET NOT NULL,
    ALTER COLUMN workspace_id SET NOT NULL, 
    ALTER COLUMN nome SET NOT NULL,
    ALTER COLUMN ativo SET DEFAULT TRUE;

ALTER TABLE unidade
    ADD CONSTRAINT pk_unidade PRIMARY KEY (id_unidade),
    ADD CONSTRAINT uq_unidade_workspace_nome UNIQUE (workspace_id, nome),
    ADD CONSTRAINT ck_unidade_nome CHECK (CHAR_LENGTH(BTRIM(nome)) >= 2);

ALTER TABLE unidade_endereco
    ALTER COLUMN unidade_id SET NOT NULL,
    ALTER COLUMN cep SET NOT NULL,
    ALTER COLUMN rua SET NOT NULL,
    ALTER COLUMN cidade SET NOT NULL,
    ALTER COLUMN bairro SET NOT NULL,
    ALTER COLUMN estado SET NOT NULL;

ALTER TABLE unidade_endereco
    ADD CONSTRAINT pk_unidade_endereco PRIMARY KEY (unidade_id),
    ADD CONSTRAINT ck_unidade_endereco_cep CHECK (cep ~ '^[0-9]{8}$'),
    ADD CONSTRAINT ck_unidade_endereco_estado CHECK (estado ~ '^[A-Z]{2}$'),
    ADD CONSTRAINT ck_unidade_endereco_rua CHECK (CHAR_LENGTH(BTRIM(rua)) >= 2),
    ADD CONSTRAINT ck_unidade_endereco_cidade CHECK (CHAR_LENGTH(BTRIM(cidade)) >= 2),
    ADD CONSTRAINT ck_unidade_endereco_bairro CHECK (CHAR_LENGTH(BTRIM(bairro)) >= 2),
    ADD CONSTRAINT ck_unidade_endereco_complemento CHECK (
        complemento IS NULL OR CHAR_LENGTH(BTRIM(complemento)) >= 1
    );

ALTER TABLE usuario
    ALTER COLUMN id_usuario SET NOT NULL,
    ALTER COLUMN nome SET NOT NULL,
    ALTER COLUMN tipo SET NOT NULL,
    ALTER COLUMN cargo_id SET NOT NULL,   
    ALTER COLUMN unidade_id SET NOT NULL, 
    ALTER COLUMN status SET DEFAULT 'PRE_CADASTRADO',
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN criado_em SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE usuario
    ADD CONSTRAINT pk_usuario PRIMARY KEY (id_usuario),
    ADD CONSTRAINT uq_usuario_email UNIQUE (email),
    ADD CONSTRAINT uq_usuario_firebase_uid UNIQUE (firebase_uid),
    ADD CONSTRAINT uq_usuario_cpf UNIQUE (cpf),
    ADD CONSTRAINT ck_usuario_cpf CHECK (cpf IS NULL OR cpf ~ '^[0-9]{11}$'),
    ADD CONSTRAINT ck_usuario_tipo CHECK (tipo IN ('GESTOR', 'GESTOR_WORKSPACE', 'COLABORADOR')),
    ADD CONSTRAINT ck_usuario_status CHECK (status IN ('PRE_CADASTRADO', 'ATIVO', 'DESATIVADO')),
    ADD CONSTRAINT ck_usuario_modalidade CHECK (
        modalidade IN ('PRESENCIAL', 'REMOTO', 'HIBRIDO')
    );

ALTER TABLE admin
    ALTER COLUMN id_admin SET NOT NULL;

ALTER TABLE admin
    ADD CONSTRAINT pk_admin PRIMARY KEY (id_admin),
    ADD CONSTRAINT uq_admin_email UNIQUE (email),
    ADD CONSTRAINT uq_admin_firebase_uid UNIQUE (firebase_uid);

ALTER TABLE usuario_foto_perfil
    ALTER COLUMN usuario_id SET NOT NULL,
    ALTER COLUMN caminho_objeto SET NOT NULL;

ALTER TABLE usuario_foto_perfil
    ADD CONSTRAINT pk_usuario_foto_perfil PRIMARY KEY (usuario_id),
    ADD CONSTRAINT uq_usuario_foto_perfil_caminho UNIQUE (caminho_objeto),
    ADD CONSTRAINT ck_usuario_foto_perfil_caminho CHECK (CHAR_LENGTH(BTRIM(caminho_objeto)) >= 1);

ALTER TABLE nr_catalogo
    ALTER COLUMN codigo_nr SET NOT NULL,
    ALTER COLUMN titulo SET NOT NULL,
    ALTER COLUMN tempo_reciclagem_mes SET NOT NULL,
    ALTER COLUMN revogada SET DEFAULT FALSE,
    ALTER COLUMN revogada SET NOT NULL;

ALTER TABLE nr_catalogo
    ADD CONSTRAINT pk_nr_catalogo PRIMARY KEY (codigo_nr),
    ADD CONSTRAINT ck_nr_catalogo_codigo CHECK (codigo_nr > 0),
    ADD CONSTRAINT ck_nr_catalogo_titulo CHECK (CHAR_LENGTH(BTRIM(titulo)) >= 3),
    ADD CONSTRAINT ck_nr_catalogo_reciclagem CHECK (tempo_reciclagem_mes > 0);

ALTER TABLE cargo_nr
    ALTER COLUMN cargo_id SET NOT NULL,
    ALTER COLUMN nr_id SET NOT NULL;

ALTER TABLE cargo_nr
    ADD CONSTRAINT pk_cargo_nr PRIMARY KEY (cargo_id, nr_id);

ALTER TABLE unidade_nr
    ALTER COLUMN unidade_id SET NOT NULL,
    ALTER COLUMN nr_id SET NOT NULL;

ALTER TABLE unidade_nr
    ADD CONSTRAINT pk_unidade_nr PRIMARY KEY (unidade_id, nr_id);

ALTER TABLE evento
    ALTER COLUMN id_evento SET NOT NULL,
    ALTER COLUMN gestor_id SET NOT NULL,
    ALTER COLUMN titulo SET NOT NULL,
    ALTER COLUMN descricao SET NOT NULL,
    ALTER COLUMN modo_conclusao SET NOT NULL,
    ALTER COLUMN evidencia_obrigatoria SET DEFAULT FALSE,
    ALTER COLUMN evidencia_obrigatoria SET NOT NULL,
    ALTER COLUMN status SET DEFAULT 'ATIVO',
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE evento
    ADD CONSTRAINT pk_evento PRIMARY KEY (id_evento),
    ADD CONSTRAINT ck_evento_titulo CHECK (CHAR_LENGTH(BTRIM(titulo)) >= 3),
    ADD CONSTRAINT ck_evento_descricao CHECK (CHAR_LENGTH(BTRIM(descricao)) >= 3),
    ADD CONSTRAINT ck_evento_link_externo CHECK (
        link_externo IS NULL OR link_externo ~* '^https?://[^[:space:]]+$'
    ),
    ADD CONSTRAINT ck_evento_modo_conclusao CHECK (
        modo_conclusao IN ('COLABORADOR', 'GESTOR', 'LISTA_PRESENCA')
    ),
    ADD CONSTRAINT ck_evento_status CHECK (
        status IN ('ATIVO', 'CONCLUIDO', 'CANCELADO')
    ),
    ADD CONSTRAINT ck_evento_cancelamento CHECK (
        (status = 'CANCELADO'
            AND data_cancelamento IS NOT NULL
            AND motivo_cancelamento IS NOT NULL
            AND CHAR_LENGTH(BTRIM(motivo_cancelamento)) >= 3)
        OR
        (status <> 'CANCELADO'
            AND data_cancelamento IS NULL
            AND motivo_cancelamento IS NULL)
    ); 

ALTER TABLE turma
    ALTER COLUMN id_turma SET NOT NULL,
    ALTER COLUMN evento_id SET NOT NULL,
    ALTER COLUMN nome SET NOT NULL,
    ALTER COLUMN data_inicial SET NOT NULL,
    ALTER COLUMN data_termino SET NOT NULL;

ALTER TABLE turma
    ADD CONSTRAINT pk_turma PRIMARY KEY (id_turma),
    ADD CONSTRAINT uq_turma_evento_nome UNIQUE (evento_id, nome),
    ADD CONSTRAINT ck_turma_nome CHECK (CHAR_LENGTH(BTRIM(nome)) >= 1),
    ADD CONSTRAINT ck_turma_periodo CHECK (data_termino > data_inicial);

ALTER TABLE turma_funcionario
    ALTER COLUMN id_turma_funcionario SET NOT NULL,
    ALTER COLUMN turma_id SET NOT NULL,
    ALTER COLUMN usuario_id SET NOT NULL;

ALTER TABLE turma_funcionario
    ADD CONSTRAINT pk_turma_funcionario PRIMARY KEY (id_turma_funcionario),
    ADD CONSTRAINT uq_turma_funcionario_participacao UNIQUE (turma_id, usuario_id); -- Impede incluir o mesmo usuário duas vezes na turma.

ALTER TABLE conclusao_evento
    ALTER COLUMN id_conclusao_evento SET NOT NULL,
    ALTER COLUMN turma_funcionario_id SET NOT NULL,
    ALTER COLUMN status SET DEFAULT 'PENDENTE',
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE conclusao_evento
    ADD CONSTRAINT pk_conclusao_evento PRIMARY KEY (id_conclusao_evento),
    ADD CONSTRAINT uq_conclusao_evento_participacao UNIQUE (turma_funcionario_id), -- Materializa a cardinalidade 1:0..1.
    ADD CONSTRAINT ck_conclusao_evento_status CHECK (
        status IN ('PENDENTE', 'CONCLUIDO', 'REJEITADO')
    ),
    ADD CONSTRAINT ck_conclusao_evento_data_conclusao CHECK (
        status <> 'CONCLUIDO' OR data_conclusao IS NOT NULL
    ),
    ADD CONSTRAINT ck_conclusao_evento_data_validacao CHECK (
        status = 'PENDENTE' OR data_validacao IS NOT NULL
    ),
    ADD CONSTRAINT ck_conclusao_evento_motivo_rejeicao CHECK (
        (status = 'REJEITADO'
            AND motivo_rejeicao IS NOT NULL
            AND CHAR_LENGTH(BTRIM(motivo_rejeicao)) >= 3)
        OR
        (status <> 'REJEITADO' AND motivo_rejeicao IS NULL)
    ),
    ADD CONSTRAINT ck_conclusao_evento_validade CHECK (
        data_validade IS NULL
        OR data_conclusao IS NULL
        OR data_validade >= data_conclusao::DATE
    );

ALTER TABLE evidencia
    ALTER COLUMN id_evidencia SET NOT NULL,
    ALTER COLUMN conclusao_evento_id SET NOT NULL,
    ALTER COLUMN nome_original SET NOT NULL,
    ALTER COLUMN caminho_objeto SET NOT NULL,
    ALTER COLUMN mime_type SET NOT NULL,
    ALTER COLUMN tamanho_byte SET NOT NULL;

ALTER TABLE evidencia
    ADD CONSTRAINT pk_evidencia PRIMARY KEY (id_evidencia),
    ADD CONSTRAINT uq_evidencia_caminho_objeto UNIQUE (caminho_objeto),
    ADD CONSTRAINT ck_evidencia_nome_original CHECK (CHAR_LENGTH(BTRIM(nome_original)) >= 1),
    ADD CONSTRAINT ck_evidencia_caminho_objeto CHECK (CHAR_LENGTH(BTRIM(caminho_objeto)) >= 1),
    ADD CONSTRAINT ck_evidencia_mime_type CHECK (
        mime_type ~ '^[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+$'
    ),
    ADD CONSTRAINT ck_evidencia_tamanho_byte CHECK (tamanho_byte > 0);

ALTER TABLE conformidade
    ALTER COLUMN id_conformidade SET NOT NULL,
    ALTER COLUMN usuario_id SET NOT NULL,
    ALTER COLUMN nr_id SET NOT NULL,
    ALTER COLUMN aplicavel SET NOT NULL,
    ALTER COLUMN origem SET NOT NULL;

ALTER TABLE conformidade
    ADD CONSTRAINT pk_conformidade PRIMARY KEY (id_conformidade),
    ADD CONSTRAINT uq_conformidade_conclusao UNIQUE (conclusao_evento_id),
    ADD CONSTRAINT ck_conformidade_origem CHECK (CHAR_LENGTH(BTRIM(origem)) >= 2);

ALTER TABLE cargo
    ADD CONSTRAINT fk_cargo_workspace FOREIGN KEY (workspace_id)
        REFERENCES workspace (id_workspace)
        ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE unidade
    ADD CONSTRAINT fk_unidade_workspace FOREIGN KEY (workspace_id)
        REFERENCES workspace (id_workspace)
        ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE unidade_endereco
    ADD CONSTRAINT fk_unidade_endereco_unidade FOREIGN KEY (unidade_id)
        REFERENCES unidade (id_unidade)
        ON UPDATE CASCADE ON DELETE CASCADE; 

ALTER TABLE usuario
    ADD CONSTRAINT fk_usuario_cargo FOREIGN KEY (cargo_id)
        REFERENCES cargo (id_cargo)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    ADD CONSTRAINT fk_usuario_unidade FOREIGN KEY (unidade_id)
        REFERENCES unidade (id_unidade)
        ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE usuario_foto_perfil
    ADD CONSTRAINT fk_usuario_foto_perfil_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario (id_usuario)
        ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE cargo_nr
    ADD CONSTRAINT fk_cargo_nr_cargo FOREIGN KEY (cargo_id)
        REFERENCES cargo (id_cargo)
        ON UPDATE CASCADE ON DELETE CASCADE,
    ADD CONSTRAINT fk_cargo_nr_nr FOREIGN KEY (nr_id)
        REFERENCES nr_catalogo (codigo_nr)
        ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE unidade_nr
    ADD CONSTRAINT fk_unidade_nr_unidade FOREIGN KEY (unidade_id)
        REFERENCES unidade (id_unidade)
        ON UPDATE CASCADE ON DELETE CASCADE,
    ADD CONSTRAINT fk_unidade_nr_nr FOREIGN KEY (nr_id)
        REFERENCES nr_catalogo (codigo_nr)
        ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE evento
    ADD CONSTRAINT fk_evento_gestor FOREIGN KEY (gestor_id)
        REFERENCES usuario (id_usuario)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    ADD CONSTRAINT fk_evento_nr FOREIGN KEY (nr_id)
        REFERENCES nr_catalogo (codigo_nr)
        ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE turma
    ADD CONSTRAINT fk_turma_evento FOREIGN KEY (evento_id)
        REFERENCES evento (id_evento)
        ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE turma_funcionario
    ADD CONSTRAINT fk_turma_funcionario_turma FOREIGN KEY (turma_id)
        REFERENCES turma (id_turma)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    ADD CONSTRAINT fk_turma_funcionario_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario (id_usuario)
        ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE conclusao_evento
    ADD CONSTRAINT fk_conclusao_evento_participacao FOREIGN KEY (turma_funcionario_id)
        REFERENCES turma_funcionario (id_turma_funcionario)
        ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE evidencia
    ADD CONSTRAINT fk_evidencia_conclusao_evento FOREIGN KEY (conclusao_evento_id)
        REFERENCES conclusao_evento (id_conclusao_evento)
        ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE conformidade
    ADD CONSTRAINT fk_conformidade_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario (id_usuario)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    ADD CONSTRAINT fk_conformidade_nr FOREIGN KEY (nr_id)
        REFERENCES nr_catalogo (codigo_nr)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    ADD CONSTRAINT fk_conformidade_conclusao FOREIGN KEY (conclusao_evento_id)
        REFERENCES conclusao_evento (id_conclusao_evento)
        ON UPDATE CASCADE ON DELETE RESTRICT;

ALTER TABLE acesso
    ALTER COLUMN data SET NOT NULL,
    ALTER COLUMN usuario_id SET NOT NULL,
    ADD CONSTRAINT pk_acesso PRIMARY KEY (data, usuario_id);

ALTER TABLE eventos_astro
    ALTER COLUMN firebase_uid SET NOT NULL,
    ALTER COLUMN tipo_evento SET NOT NULL,
    ALTER COLUMN criado_em SET NOT NULL,
    ALTER COLUMN criado_em SET DEFAULT CURRENT_DATE,
    ADD CONSTRAINT pk_eventos_astro PRIMARY KEY (id_evento);
