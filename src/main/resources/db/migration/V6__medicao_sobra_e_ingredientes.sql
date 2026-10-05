-- Medicao de sobra e composicao das receitas.
--
-- Ate aqui o sistema so sabia quantas autorizacoes a catraca registrou. Isso nao mede nem
-- aceitacao nem desperdicio, e por isso os dois indicadores saiam NAO_AVALIAVEL sempre.
--
-- As duas colunas de perda sao separadas de proposito:
--   sobra_nao_distribuida = ficou na cuba, nunca chegou ao prato -> erro de producao
--   resto_no_prato        = foi servido e voltou                 -> rejeicao alimentar
-- Somar as duas num unico "desperdicio" misturaria falha de planejamento com recusa do aluno,
-- que e justamente a confusao que este bloco existe para desfazer.
--
-- Unidade: porcoes (refeicao-equivalente), a mesma dos consumos. O cardapio registra "15 kg",
-- mas converter kg em porcoes exigiria fator por receita, que o prototipo nao tem.
--
-- escola_id acompanha historico_consumo: a analise por ingrediente precisa de amostra, e uma
-- escola sozinha rende pouco mais de uma medicao por dia util. A escola 1 e a do prototipo e a
-- unica que recebe lancamento pelo endpoint; as demais so existem como serie historica.
CREATE TABLE medicao_sobra (
    id UUID PRIMARY KEY,
    data DATE NOT NULL,
    turno VARCHAR(20) NOT NULL,
    escola_id INTEGER NOT NULL,
    item VARCHAR(120) NOT NULL,
    porcoes_preparadas INTEGER NOT NULL CHECK (porcoes_preparadas >= 0),
    porcoes_servidas INTEGER NOT NULL CHECK (porcoes_servidas >= 0),
    sobra_nao_distribuida INTEGER NOT NULL CHECK (sobra_nao_distribuida >= 0),
    resto_no_prato INTEGER NOT NULL CHECK (resto_no_prato >= 0),
    origem VARCHAR(20) NOT NULL,
    registrado_em TIMESTAMP NOT NULL,
    CONSTRAINT uk_medicao_sobra UNIQUE (data, turno, escola_id, item)
);

CREATE INDEX idx_medicao_sobra_data_turno ON medicao_sobra(data, turno);
CREATE INDEX idx_medicao_sobra_item ON medicao_sobra(item);

-- Composicao das receitas. Sem isso o sistema so conhece "Macarrao ao sugo" e nao tem como
-- associar queda de aceitacao a um ingrediente especifico.
CREATE TABLE receita_ingrediente (
    id UUID PRIMARY KEY,
    item VARCHAR(120) NOT NULL,
    ingrediente VARCHAR(80) NOT NULL,
    CONSTRAINT uk_receita_ingrediente UNIQUE (item, ingrediente)
);

CREATE INDEX idx_receita_ingrediente ON receita_ingrediente(ingrediente);
