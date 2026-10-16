CREATE TABLE workspace (
    id_workspace BIGSERIAL,
    nome VARCHAR(255),
    cnpj VARCHAR(14)
);

COMMENT ON TABLE workspace IS 'Empresas ou ambientes clientes que isolam os dados de cargos, unidades, usuários e eventos no Astro.';
COMMENT ON COLUMN workspace.id_workspace IS 'Identificador interno e autoincrementado do workspace.';
COMMENT ON COLUMN workspace.nome IS 'Nome empresarial ou nome de exibição do workspace.';
COMMENT ON COLUMN workspace.cnpj IS 'CNPJ do workspace, armazenado somente com os 14 dígitos.';

CREATE TABLE cargo (
    id_cargo BIGSERIAL,
    workspace_id BIGINT,
    nome VARCHAR(255),
    ativo BOOLEAN
);

COMMENT ON TABLE cargo IS 'Cargos profissionais cadastrados dentro de um workspace.';
COMMENT ON COLUMN cargo.id_cargo IS 'Identificador interno e autoincrementado do cargo.';
COMMENT ON COLUMN cargo.workspace_id IS 'Workspace proprietário do cargo.';
COMMENT ON COLUMN cargo.nome IS 'Nome do cargo exercido pelo usuário.';

CREATE TABLE unidade (
    id_unidade BIGSERIAL,
    workspace_id BIGINT,
    nome VARCHAR(255),
    ativo BOOLEAN
);

COMMENT ON TABLE unidade IS 'Estabelecimentos ou unidades organizacionais pertencentes a um workspace.';
COMMENT ON COLUMN unidade.id_unidade IS 'Identificador interno e autoincrementado da unidade.';
COMMENT ON COLUMN unidade.workspace_id IS 'Workspace ao qual a unidade pertence.';
COMMENT ON COLUMN unidade.nome IS 'Nome de identificação da unidade.';

CREATE TABLE unidade_endereco (
    unidade_id BIGINT,
    cep VARCHAR(8),
    rua VARCHAR(255),
    cidade VARCHAR(255),
    bairro VARCHAR(255),
    estado VARCHAR(2),
    complemento VARCHAR(255)
);

COMMENT ON TABLE unidade_endereco IS 'Endereço opcional e único de cada unidade.';
COMMENT ON COLUMN unidade_endereco.unidade_id IS 'Unidade à qual o endereço pertence; também identifica unicamente o endereço.';
COMMENT ON COLUMN unidade_endereco.cep IS 'CEP do endereço, armazenado somente com os 8 dígitos.';
COMMENT ON COLUMN unidade_endereco.rua IS 'Nome da rua, avenida ou logradouro da unidade.';
COMMENT ON COLUMN unidade_endereco.cidade IS 'Cidade onde a unidade está localizada.';
COMMENT ON COLUMN unidade_endereco.bairro IS 'Bairro onde a unidade está localizada.';
COMMENT ON COLUMN unidade_endereco.estado IS 'Sigla da unidade federativa em duas letras maiúsculas.';
COMMENT ON COLUMN unidade_endereco.complemento IS 'Informação complementar e opcional do endereço.';

CREATE TABLE conta (
    nome VARCHAR(255),
    email VARCHAR(255),
    firebase_uid VARCHAR(128)
);

COMMENT ON TABLE conta IS 'Dados comuns de identificação e autenticação herdados por usuários e administradores.';
COMMENT ON COLUMN conta.nome IS 'Nome completo da conta.';
COMMENT ON COLUMN conta.email IS 'Endereço de e-mail usado para identificação e autenticação.';
COMMENT ON COLUMN conta.firebase_uid IS 'Identificador único da conta no Firebase Authentication.';

CREATE TABLE usuario (
    id_usuario BIGSERIAL,
    tipo VARCHAR(50),
    cargo_id BIGINT,
    unidade_id BIGINT,
    cpf CHAR(11),
    modalidade VARCHAR(50),
    status VARCHAR(50),
    criado_em TIMESTAMP
) INHERITS (conta);

COMMENT ON TABLE usuario IS 'Contas dos gestores, gestores de workspace e funcionários que utilizam o Astro.';
COMMENT ON COLUMN usuario.id_usuario IS 'Identificador interno e autoincrementado do usuário.';
COMMENT ON COLUMN usuario.tipo IS 'Perfil de acesso do usuário no Astro.';
COMMENT ON COLUMN usuario.cargo_id IS 'Cargo ao qual o usuário está vinculado.';
COMMENT ON COLUMN usuario.unidade_id IS 'Unidade na qual o usuário está alocado.';
COMMENT ON COLUMN usuario.cpf IS 'CPF do usuário, armazenado somente com os 11 dígitos.';
COMMENT ON COLUMN usuario.modalidade IS 'Modalidade de trabalho ou vínculo informada para o usuário.';
COMMENT ON COLUMN usuario.status IS 'Situação atual do cadastro e do acesso do usuário.';

CREATE TABLE admin (
    id_admin BIGSERIAL
) INHERITS (conta);

COMMENT ON TABLE admin IS 'Administradores técnicos da plataforma, independentes dos usuários de cada workspace.';
COMMENT ON COLUMN admin.id_admin IS 'Identificador interno e autoincrementado do administrador.';

CREATE TABLE usuario_foto_perfil (
    usuario_id BIGINT,
    caminho_objeto VARCHAR(2048)
);

COMMENT ON TABLE usuario_foto_perfil IS
'Armazena a referência da foto de perfil do usuário mantida no bucket avatars do Supabase Storage.';

COMMENT ON COLUMN usuario_foto_perfil.usuario_id IS
'Identificador do usuário proprietário da foto de perfil.';

COMMENT ON COLUMN usuario_foto_perfil.caminho_objeto IS
'Caminho permanente da imagem dentro do bucket avatars do Supabase Storage. Não deve armazenar URL pública ou assinada.';

CREATE TABLE nr_catalogo (
    codigo_nr INTEGER,
    titulo VARCHAR(255),
    tempo_reciclagem_mes INTEGER,
    revogada BOOLEAN
);

COMMENT ON TABLE nr_catalogo IS 'Catálogo central de Normas Regulamentadoras conhecidas pelo Astro.';
COMMENT ON COLUMN nr_catalogo.codigo_nr IS 'Número oficial que identifica a Norma Regulamentadora.';
COMMENT ON COLUMN nr_catalogo.titulo IS 'Título oficial ou nome resumido da Norma Regulamentadora.';
COMMENT ON COLUMN nr_catalogo.tempo_reciclagem_mes IS 'Intervalo padrão, em meses, para reciclagem ou renovação relacionada à NR.';
COMMENT ON COLUMN nr_catalogo.revogada IS 'Indica se a Norma Regulamentadora foi revogada.';

CREATE TABLE cargo_nr (
    cargo_id BIGINT,
    nr_id INTEGER
);

COMMENT ON TABLE cargo_nr IS 'Associa cargos às Normas Regulamentadoras aplicáveis, materializando o relacionamento N:N.';
COMMENT ON COLUMN cargo_nr.cargo_id IS 'Cargo participante da associação.';
COMMENT ON COLUMN cargo_nr.nr_id IS 'Norma Regulamentadora associada ao cargo.';

CREATE TABLE unidade_nr (
    unidade_id BIGINT,
    nr_id INTEGER
);

COMMENT ON TABLE unidade_nr IS 'Associa unidades às Normas Regulamentadoras aplicáveis, materializando o relacionamento N:N.';
COMMENT ON COLUMN unidade_nr.unidade_id IS 'Unidade participante da associação.';
COMMENT ON COLUMN unidade_nr.nr_id IS 'Norma Regulamentadora associada à unidade.';

CREATE TABLE evento (
    id_evento BIGSERIAL,
    gestor_id BIGINT,
    nr_id INTEGER,
    titulo VARCHAR(255),
    descricao TEXT,
    link_externo VARCHAR(2048),
    modo_conclusao VARCHAR(50),
    evidencia_obrigatoria BOOLEAN,
    status VARCHAR(50),
    data_cancelamento TIMESTAMP,
    motivo_cancelamento TEXT
);

COMMENT ON TABLE evento IS 'Eventos de treinamento ou conformidade criados por gestores e posteriormente divididos em turmas.';
COMMENT ON COLUMN evento.id_evento IS 'Identificador interno e autoincrementado do evento.';
COMMENT ON COLUMN evento.gestor_id IS 'Usuário gestor responsável pela criação do evento.';
COMMENT ON COLUMN evento.nr_id IS 'Norma Regulamentadora opcionalmente relacionada ao evento.';
COMMENT ON COLUMN evento.titulo IS 'Título comum a todas as turmas do evento.';
COMMENT ON COLUMN evento.descricao IS 'Descrição e orientações gerais do evento.';
COMMENT ON COLUMN evento.link_externo IS 'Link opcional para conteúdo, reunião ou material externo.';
COMMENT ON COLUMN evento.modo_conclusao IS 'Forma pela qual a participação será concluída: colaborador, gestor ou lista de presença.';
COMMENT ON COLUMN evento.evidencia_obrigatoria IS 'Indica se a conclusão exige o envio de uma evidência.';
COMMENT ON COLUMN evento.status IS 'Situação atual do evento em seu ciclo de vida.';
COMMENT ON COLUMN evento.data_cancelamento IS 'Data e horário em que o evento foi cancelado, quando aplicável.';
COMMENT ON COLUMN evento.motivo_cancelamento IS 'Justificativa registrada para o cancelamento do evento.';

CREATE TABLE eventos_astro (
    id_evento BIGSERIAL,
    firebase_uid VARCHAR,
    tipo_evento VARCHAR,
    nome_botao VARCHAR,
    nome_tela VARCHAR,
    contexto_tela TEXT,
    nome_dialog VARCHAR,
    dialog_clicado VARCHAR,
    showcase_click VARCHAR,
    nome_showcase VARCHAR,
    criado_em TIMESTAMP
);

COMMENT ON TABLE eventos_astro IS 'Armazena todos os eventos da aplicação web do Astro.';
COMMENT ON COLUMN eventos_astro.id_evento IS 'Identificador interno e autoincrementado do evento web.';
COMMENT ON COLUMN eventos_astro.firebase_uid IS 'Identificador do usuário no Firebase associado ao evento web.';
COMMENT ON COLUMN eventos_astro.tipo_evento IS 'Tipo do evento registrado na aplicação web.';
COMMENT ON COLUMN eventos_astro.nome_botao IS 'Nome do botão associado ao evento.';
COMMENT ON COLUMN eventos_astro.nome_tela IS 'Nome da tela em que o evento ocorreu.';
COMMENT ON COLUMN eventos_astro.contexto_tela IS 'Contexto da tela no momento do evento.';
COMMENT ON COLUMN eventos_astro.nome_dialog IS 'Nome do diálogo associado ao evento.';
COMMENT ON COLUMN eventos_astro.dialog_clicado IS 'Registro do clique no diálogo.';
COMMENT ON COLUMN eventos_astro.showcase_click IS 'Registro do clique no showcase.';
COMMENT ON COLUMN eventos_astro.nome_showcase IS 'Nome do showcase associado ao evento.';
COMMENT ON COLUMN eventos_astro.criado_em IS 'Data e horário de criação do registro; por padrão, recebe a data atual à meia-noite.';

CREATE TABLE turma (
    id_turma BIGSERIAL,
    evento_id BIGINT,
    nome VARCHAR(255),
    data_inicial TIMESTAMP,
    data_termino TIMESTAMP
);

COMMENT ON TABLE turma IS 'Turmas de um evento, cada uma com programação própria de data e horário.';
COMMENT ON COLUMN turma.id_turma IS 'Identificador interno e autoincrementado da turma.';
COMMENT ON COLUMN turma.evento_id IS 'Evento ao qual a turma pertence.';
COMMENT ON COLUMN turma.nome IS 'Nome usado para distinguir a turma dentro do evento.';
COMMENT ON COLUMN turma.data_inicial IS 'Data e horário de início da turma.';
COMMENT ON COLUMN turma.data_termino IS 'Data e horário de encerramento da turma.';

CREATE TABLE turma_funcionario (
    id_turma_funcionario BIGSERIAL,
    turma_id BIGINT,
    usuario_id BIGINT
);

COMMENT ON TABLE turma_funcionario IS 'Participações de usuários funcionários nas turmas dos eventos.';
COMMENT ON COLUMN turma_funcionario.id_turma_funcionario IS 'Identificador interno e autoincrementado da participação.';
COMMENT ON COLUMN turma_funcionario.turma_id IS 'Turma na qual o funcionário foi incluído.';
COMMENT ON COLUMN turma_funcionario.usuario_id IS 'Usuário funcionário participante da turma.';

CREATE TABLE conclusao_evento (
    id_conclusao_evento BIGSERIAL,
    turma_funcionario_id BIGINT,
    status VARCHAR(50),
    data_conclusao TIMESTAMP,
    data_validacao TIMESTAMP,
    data_validade DATE,
    motivo_rejeicao TEXT
);

COMMENT ON TABLE conclusao_evento IS 'Registros de conclusão e validação da participação de um funcionário em uma turma.';
COMMENT ON COLUMN conclusao_evento.id_conclusao_evento IS 'Identificador interno e autoincrementado da conclusão.';
COMMENT ON COLUMN conclusao_evento.turma_funcionario_id IS 'Participação em turma à qual a conclusão pertence.';
COMMENT ON COLUMN conclusao_evento.status IS 'Situação da conclusão durante o processo de validação.';
COMMENT ON COLUMN conclusao_evento.data_conclusao IS 'Data e horário em que a participação foi marcada como concluída.';
COMMENT ON COLUMN conclusao_evento.data_validacao IS 'Data e horário em que a conclusão foi validada ou rejeitada.';
COMMENT ON COLUMN conclusao_evento.data_validade IS 'Data até a qual a conclusão ou certificação permanece válida.';
COMMENT ON COLUMN conclusao_evento.motivo_rejeicao IS 'Justificativa registrada quando a conclusão é rejeitada.';

CREATE TABLE evidencia (
    id_evidencia BIGSERIAL,
    conclusao_evento_id BIGINT,
    nome_original VARCHAR(255),
    caminho_objeto VARCHAR(2048),
    mime_type VARCHAR(127),
    tamanho_byte BIGINT
);

COMMENT ON TABLE evidencia IS
'Armazena os metadados dos arquivos utilizados como evidência de conclusão de eventos. O conteúdo do arquivo permanece no Supabase Storage.';

COMMENT ON COLUMN evidencia.id_evidencia IS
'Identificador único da evidência gerado automaticamente pelo PostgreSQL.';

COMMENT ON COLUMN evidencia.conclusao_evento_id IS
'Identificador da conclusão de evento à qual o arquivo de evidência pertence.';

COMMENT ON COLUMN evidencia.nome_original IS
'Nome original do arquivo informado pelo dispositivo do usuário, utilizado apenas para exibição e download.';

COMMENT ON COLUMN evidencia.caminho_objeto IS
'Caminho permanente do objeto dentro do bucket evidencias do Supabase Storage. Não deve armazenar uma URL pública ou assinada.';

COMMENT ON COLUMN evidencia.mime_type IS
'Tipo de conteúdo do arquivo, como application/pdf, image/jpeg ou application/vnd.openxmlformats-officedocument.wordprocessingml.document.';

COMMENT ON COLUMN evidencia.tamanho_byte IS
'Tamanho total do arquivo em bytes.';

CREATE TABLE conformidade (
    id_conformidade BIGSERIAL,
    usuario_id BIGINT,
    nr_id INTEGER,
    aplicavel BOOLEAN,
    data_validade DATE,
    origem VARCHAR(50),
    conclusao_evento_id BIGINT
);

COMMENT ON TABLE conformidade IS 'Estado de conformidade de um usuário em relação a uma Norma Regulamentadora.';
COMMENT ON COLUMN conformidade.id_conformidade IS 'Identificador interno e autoincrementado do registro de conformidade.';
COMMENT ON COLUMN conformidade.usuario_id IS 'Usuário ao qual a conformidade pertence.';
COMMENT ON COLUMN conformidade.nr_id IS 'Norma Regulamentadora avaliada no registro de conformidade.';
COMMENT ON COLUMN conformidade.aplicavel IS 'Indica se a NR é aplicável ao usuário no contexto avaliado.';
COMMENT ON COLUMN conformidade.data_validade IS 'Data limite da conformidade, quando houver validade definida.';
COMMENT ON COLUMN conformidade.origem IS 'Origem do registro, como conclusão de evento ou registro administrativo.';
COMMENT ON COLUMN conformidade.conclusao_evento_id IS 'Conclusão que originou a conformidade, quando existir.';

CREATE TABLE acesso (
    data DATE,
    usuario_id INTEGER
);

COMMENT ON TABLE acesso IS
'Registra os acessos diários dos usuários para permitir o cálculo da métrica DAU (Daily Active Users).';

COMMENT ON COLUMN acesso.data IS
'Data em que o usuário acessou o sistema. Utilizada para agrupar e calcular os usuários ativos por dia.';

COMMENT ON COLUMN acesso.usuario_id IS
'Identificador do usuário que acessou o sistema na data registrada.';
