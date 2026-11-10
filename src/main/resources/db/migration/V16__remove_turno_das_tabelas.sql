-- Contract: a refeicao (V15) substitui o turno nas tabelas operacionais. relatorio_ia.turno fica
-- para os relatorios gerados antes da refeicao continuarem legiveis.
DROP INDEX idx_cardapio_data_turno_ativo;
DROP INDEX idx_auditoria_data_turno;
DROP INDEX idx_medicao_sobra_data_turno;
DROP INDEX idx_historico_data_turno;

ALTER TABLE medicao_sobra DROP CONSTRAINT uk_medicao_sobra;

ALTER TABLE cardapio DROP COLUMN turno;
ALTER TABLE auditoria_consumo DROP COLUMN turno;
ALTER TABLE medicao_sobra DROP COLUMN turno;
ALTER TABLE historico_consumo DROP COLUMN turno;
