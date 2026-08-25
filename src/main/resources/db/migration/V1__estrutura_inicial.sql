CREATE TABLE aluno (
    id UUID PRIMARY KEY,
    codigo_publico VARCHAR(40) NOT NULL UNIQUE,
    matricula VARCHAR(40) NOT NULL UNIQUE,
    nome VARCHAR(150) NOT NULL,
    turma VARCHAR(80) NOT NULL,
    ativo BOOLEAN NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE biometria_facial (
    id UUID PRIMARY KEY,
    aluno_id UUID NOT NULL UNIQUE,
    descriptor BINARY LARGE OBJECT NOT NULL,
    versao_modelo VARCHAR(50) NOT NULL,
    consentimento_registrado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_biometria_aluno FOREIGN KEY (aluno_id) REFERENCES aluno(id)
);

CREATE TABLE cardapio (
    id UUID PRIMARY KEY,
    data DATE NOT NULL,
    turno VARCHAR(20) NOT NULL,
    nome_refeicao VARCHAR(150) NOT NULL,
    descricao VARCHAR(500),
    itens_json CHARACTER LARGE OBJECT NOT NULL,
    quantidade_planejada INTEGER NOT NULL CHECK (quantidade_planejada >= 0),
    ativo BOOLEAN NOT NULL,
    CONSTRAINT uk_cardapio_data_turno UNIQUE (data, turno)
);

CREATE TABLE auditoria_consumo (
    id UUID PRIMARY KEY,
    aluno_id UUID,
    cardapio_id UUID,
    data_operacional DATE NOT NULL,
    turno VARCHAR(20) NOT NULL,
    instante TIMESTAMP WITH TIME ZONE NOT NULL,
    metodo_identificacao VARCHAR(20) NOT NULL,
    resultado VARCHAR(20) NOT NULL,
    motivo VARCHAR(60),
    correlation_id UUID NOT NULL,
    chave_consumo_autorizado VARCHAR(150) UNIQUE,
    CONSTRAINT fk_auditoria_aluno FOREIGN KEY (aluno_id) REFERENCES aluno(id),
    CONSTRAINT fk_auditoria_cardapio FOREIGN KEY (cardapio_id) REFERENCES cardapio(id)
);

CREATE INDEX idx_auditoria_data_turno ON auditoria_consumo(data_operacional, turno);

CREATE TABLE relatorio_ia (
    id UUID PRIMARY KEY,
    data_referencia DATE NOT NULL,
    turno VARCHAR(20),
    status VARCHAR(20) NOT NULL,
    provedor VARCHAR(40),
    modelo VARCHAR(100),
    prompt_versao VARCHAR(30) NOT NULL,
    dados_entrada_json CHARACTER LARGE OBJECT,
    resultado_json CHARACTER LARGE OBJECT,
    resumo CHARACTER LARGE OBJECT,
    erro CHARACTER LARGE OBJECT,
    tentativas INTEGER NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL,
    iniciado_em TIMESTAMP WITH TIME ZONE,
    concluido_em TIMESTAMP WITH TIME ZONE
);
