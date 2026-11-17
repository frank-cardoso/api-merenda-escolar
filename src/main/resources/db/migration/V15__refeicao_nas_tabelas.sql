-- Expand: a refeicao passa a ser a unidade das tabelas que hoje usam o turno. O turno continua
-- nas colunas (agora opcional) ate a V16 remove-lo de vez.
ALTER TABLE cardapio ADD COLUMN tipo_refeicao_id UUID;
ALTER TABLE auditoria_consumo ADD COLUMN tipo_refeicao_id UUID;
ALTER TABLE auditoria_consumo ADD COLUMN refeicao_nome VARCHAR(60);
ALTER TABLE medicao_sobra ADD COLUMN tipo_refeicao_id UUID;
ALTER TABLE relatorio_ia ADD COLUMN tipo_refeicao_id UUID;
ALTER TABLE historico_consumo ADD COLUMN tipo_refeicao_id UUID;

-- Turno -> refeicao: MANHA lanche da manha, TARDE lanche da tarde, NOITE lanche da noite, INTEGRAL almoco.
UPDATE cardapio SET tipo_refeicao_id = CASE turno
    WHEN 'MANHA' THEN CAST('5e1a0000-0000-4000-8000-000000000002' AS UUID)
    WHEN 'TARDE' THEN CAST('5e1a0000-0000-4000-8000-000000000004' AS UUID)
    WHEN 'NOITE' THEN CAST('5e1a0000-0000-4000-8000-000000000005' AS UUID)
    WHEN 'INTEGRAL' THEN CAST('5e1a0000-0000-4000-8000-000000000003' AS UUID)
END;

UPDATE auditoria_consumo SET tipo_refeicao_id = CASE turno
    WHEN 'MANHA' THEN CAST('5e1a0000-0000-4000-8000-000000000002' AS UUID)
    WHEN 'TARDE' THEN CAST('5e1a0000-0000-4000-8000-000000000004' AS UUID)
    WHEN 'NOITE' THEN CAST('5e1a0000-0000-4000-8000-000000000005' AS UUID)
    WHEN 'INTEGRAL' THEN CAST('5e1a0000-0000-4000-8000-000000000003' AS UUID)
END;

UPDATE auditoria_consumo SET refeicao_nome = (
    SELECT nome FROM tipo_refeicao WHERE tipo_refeicao.id = auditoria_consumo.tipo_refeicao_id);

UPDATE medicao_sobra SET tipo_refeicao_id = CASE turno
    WHEN 'MANHA' THEN CAST('5e1a0000-0000-4000-8000-000000000002' AS UUID)
    WHEN 'TARDE' THEN CAST('5e1a0000-0000-4000-8000-000000000004' AS UUID)
    WHEN 'NOITE' THEN CAST('5e1a0000-0000-4000-8000-000000000005' AS UUID)
    WHEN 'INTEGRAL' THEN CAST('5e1a0000-0000-4000-8000-000000000003' AS UUID)
END;

UPDATE relatorio_ia SET tipo_refeicao_id = CASE turno
    WHEN 'MANHA' THEN CAST('5e1a0000-0000-4000-8000-000000000002' AS UUID)
    WHEN 'TARDE' THEN CAST('5e1a0000-0000-4000-8000-000000000004' AS UUID)
    WHEN 'NOITE' THEN CAST('5e1a0000-0000-4000-8000-000000000005' AS UUID)
    WHEN 'INTEGRAL' THEN CAST('5e1a0000-0000-4000-8000-000000000003' AS UUID)
END;

-- Nome do legado -> refeicao, por prefixo. O que nao casa (frutas, turno estendido, "manha e tarde")
-- fica sem refeicao e fora da analise por refeicao.
UPDATE historico_consumo SET tipo_refeicao_id = CASE
    WHEN refeicao LIKE 'Cafe d%' THEN CAST('5e1a0000-0000-4000-8000-000000000001' AS UUID)
    WHEN refeicao LIKE 'Lanche da manha%' THEN CAST('5e1a0000-0000-4000-8000-000000000002' AS UUID)
    WHEN refeicao LIKE 'Almoco%' THEN CAST('5e1a0000-0000-4000-8000-000000000003' AS UUID)
    WHEN refeicao LIKE 'Lanche da tarde%' THEN CAST('5e1a0000-0000-4000-8000-000000000004' AS UUID)
END;

ALTER TABLE cardapio ALTER COLUMN tipo_refeicao_id SET NOT NULL;
ALTER TABLE auditoria_consumo ALTER COLUMN tipo_refeicao_id SET NOT NULL;
ALTER TABLE medicao_sobra ALTER COLUMN tipo_refeicao_id SET NOT NULL;

ALTER TABLE cardapio ALTER COLUMN turno SET NULL;
ALTER TABLE auditoria_consumo ALTER COLUMN turno SET NULL;
ALTER TABLE medicao_sobra ALTER COLUMN turno SET NULL;
ALTER TABLE historico_consumo ALTER COLUMN turno SET NULL;

ALTER TABLE cardapio ADD CONSTRAINT fk_cardapio_tipo_refeicao FOREIGN KEY (tipo_refeicao_id) REFERENCES tipo_refeicao(id);
ALTER TABLE auditoria_consumo ADD CONSTRAINT fk_auditoria_tipo_refeicao FOREIGN KEY (tipo_refeicao_id) REFERENCES tipo_refeicao(id);
ALTER TABLE medicao_sobra ADD CONSTRAINT fk_medicao_sobra_tipo_refeicao FOREIGN KEY (tipo_refeicao_id) REFERENCES tipo_refeicao(id);
ALTER TABLE relatorio_ia ADD CONSTRAINT fk_relatorio_ia_tipo_refeicao FOREIGN KEY (tipo_refeicao_id) REFERENCES tipo_refeicao(id);
ALTER TABLE historico_consumo ADD CONSTRAINT fk_historico_tipo_refeicao FOREIGN KEY (tipo_refeicao_id) REFERENCES tipo_refeicao(id);

CREATE INDEX idx_cardapio_data_refeicao_ativo ON cardapio(data, tipo_refeicao_id, ativo);
CREATE INDEX idx_auditoria_data_refeicao ON auditoria_consumo(data_operacional, tipo_refeicao_id);
CREATE INDEX idx_historico_data_refeicao ON historico_consumo(data, tipo_refeicao_id);
ALTER TABLE medicao_sobra ADD CONSTRAINT uk_medicao_sobra_refeicao UNIQUE (data, tipo_refeicao_id, escola_id, receita_id);
