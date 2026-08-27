ALTER TABLE relatorio_ia ADD COLUMN versao BIGINT DEFAULT 0 NOT NULL;

CREATE INDEX idx_relatorio_ia_data_turno
    ON relatorio_ia(data_referencia, turno, criado_em);
