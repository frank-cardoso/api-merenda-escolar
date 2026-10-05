-- Historico de consumo para alimentar a analise de IA.
--
-- Sem esse historico, o modelo recebe um unico ponto de dado e toda analise termina em
-- "historico insuficiente". Aqui ficam registros sinteticos gerados por
-- scripts/gerar_historico_fake.py, que reproduz os padroes estatisticos medidos no sistema
-- legado sem trazer nenhum dado real de producao.
--
-- Grao: um registro por (data, turno, escola, refeicao, item) — o mesmo grao do legado
-- (REFEICOES_SERVIDAS x REFEICOES_SERVIDAS_QTDS).
--
-- quantidade_servida NULL significa "planejado sem execucao registrada", que no legado e o
-- caso mais comum (~69% das linhas). Nao e dado faltante: e o estado normal de um item que
-- foi planejado e cuja execucao a escola nao lancou.
CREATE TABLE historico_consumo (
    id UUID PRIMARY KEY,
    data DATE NOT NULL,
    turno VARCHAR(20) NOT NULL,
    escola_id INTEGER NOT NULL,
    refeicao VARCHAR(150) NOT NULL,
    item VARCHAR(120) NOT NULL,
    quantidade_planejada INTEGER NOT NULL CHECK (quantidade_planejada >= 0),
    quantidade_servida INTEGER CHECK (quantidade_servida >= 0),
    origem VARCHAR(20) NOT NULL
);

CREATE INDEX idx_historico_data_turno ON historico_consumo(data, turno);
CREATE INDEX idx_historico_item ON historico_consumo(item);
