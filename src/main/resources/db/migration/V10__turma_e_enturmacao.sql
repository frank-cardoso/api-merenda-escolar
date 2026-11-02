CREATE TABLE turma (
    id UUID PRIMARY KEY,
    codigo VARCHAR(40) NOT NULL UNIQUE,
    descricao VARCHAR(150) NOT NULL,
    turno VARCHAR(20) NOT NULL,
    quantidade_maxima_alunos INTEGER CHECK (quantidade_maxima_alunos IS NULL OR quantidade_maxima_alunos > 0),
    codigo_origem VARCHAR(40),
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE enturmacao (
    id UUID PRIMARY KEY,
    aluno_id UUID NOT NULL,
    turma_id UUID NOT NULL,
    data_inicio DATE NOT NULL,
    data_fim DATE,
    codigo_origem VARCHAR(40),
    CONSTRAINT fk_enturmacao_aluno FOREIGN KEY (aluno_id) REFERENCES aluno(id),
    CONSTRAINT fk_enturmacao_turma FOREIGN KEY (turma_id) REFERENCES turma(id),
    CONSTRAINT uk_enturmacao_aluno_inicio UNIQUE (aluno_id, data_inicio),
    CONSTRAINT ck_enturmacao_vigencia CHECK (data_fim IS NULL OR data_fim >= data_inicio)
);

CREATE INDEX idx_enturmacao_turma ON enturmacao(turma_id);

ALTER TABLE aluno DROP COLUMN turma;
ALTER TABLE aluno ADD COLUMN codigo_origem VARCHAR(40);

ALTER TABLE auditoria_consumo ADD COLUMN turma_id UUID;
ALTER TABLE auditoria_consumo ADD COLUMN turma_nome VARCHAR(80);
