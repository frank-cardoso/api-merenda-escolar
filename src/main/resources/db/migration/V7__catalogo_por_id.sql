-- Identidade de item por id, nao por nome.
--
-- Ate aqui item era VARCHAR livre em tres tabelas, casado por nome. Isso ja produziu o defeito
-- registrado no item 3 da RETROSPECTIVA: o vocabulario do cardapio ("arroz") nao casava com o do
-- historico ("Arroz branco"), intersecao zero, e a analise correlacionou coisas sem relacao.
--
-- O conserto foi normalizar com comparacao por substring, o que criou um problema novo: passamos a
-- ter duas regras incompativeis de casar item no mesmo sistema (substring no historico, igualdade
-- exata na medicao). E e o mesmo vicio que criticamos no legado, onde TURMA e VARCHAR2(80) livre
-- com 433 valores distintos para 22 escolas.
--
-- Agora o vinculo e sempre por `receita_id`; `receita.nome` e rotulo de exibicao e pode mudar sem
-- quebrar junção alguma. `codigo_externo` e onde o RECEITAS.ID do legado entra numa integracao.
CREATE TABLE receita (
    id UUID PRIMARY KEY,
    codigo_externo VARCHAR(40),
    nome VARCHAR(120) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_receita_nome UNIQUE (nome)
);

CREATE TABLE ingrediente (
    id UUID PRIMARY KEY,
    codigo_externo VARCHAR(40),
    nome VARCHAR(80) NOT NULL,
    CONSTRAINT uk_ingrediente_nome UNIQUE (nome)
);

-- As tres tabelas abaixo sao recriadas em vez de alteradas: todas tem conteudo sintetico recarregado
-- dos TSV por loader, e uma coluna de texto nao se converte em FK sem uma passada de casamento por
-- nome — exatamente o que este changeset existe para eliminar.
--
-- ATENCAO: medicao_sobra pode ter linhas com origem LANCADA, que sao lancamento manual e nao voltam
-- pelo loader. Exporte antes se houver medicao real na base.
DROP TABLE receita_ingrediente;
DROP TABLE medicao_sobra;
DROP TABLE historico_consumo;

CREATE TABLE receita_ingrediente (
    id UUID PRIMARY KEY,
    receita_id UUID NOT NULL REFERENCES receita(id),
    ingrediente_id UUID NOT NULL REFERENCES ingrediente(id),
    CONSTRAINT uk_receita_ingrediente UNIQUE (receita_id, ingrediente_id)
);

CREATE INDEX idx_receita_ingrediente_ingrediente ON receita_ingrediente(ingrediente_id);

CREATE TABLE historico_consumo (
    id UUID PRIMARY KEY,
    data DATE NOT NULL,
    turno VARCHAR(20) NOT NULL,
    escola_id INTEGER NOT NULL,
    refeicao VARCHAR(150) NOT NULL,
    receita_id UUID NOT NULL REFERENCES receita(id),
    quantidade_planejada INTEGER NOT NULL CHECK (quantidade_planejada >= 0),
    quantidade_servida INTEGER CHECK (quantidade_servida >= 0),
    origem VARCHAR(20) NOT NULL
);

CREATE INDEX idx_historico_data_turno ON historico_consumo(data, turno);
CREATE INDEX idx_historico_receita ON historico_consumo(receita_id);

CREATE TABLE medicao_sobra (
    id UUID PRIMARY KEY,
    data DATE NOT NULL,
    turno VARCHAR(20) NOT NULL,
    escola_id INTEGER NOT NULL,
    receita_id UUID NOT NULL REFERENCES receita(id),
    porcoes_preparadas INTEGER NOT NULL CHECK (porcoes_preparadas >= 0),
    porcoes_servidas INTEGER NOT NULL CHECK (porcoes_servidas >= 0),
    sobra_nao_distribuida INTEGER NOT NULL CHECK (sobra_nao_distribuida >= 0),
    resto_no_prato INTEGER NOT NULL CHECK (resto_no_prato >= 0),
    origem VARCHAR(20) NOT NULL,
    registrado_em TIMESTAMP NOT NULL,
    CONSTRAINT uk_medicao_sobra UNIQUE (data, turno, escola_id, receita_id)
);

CREATE INDEX idx_medicao_sobra_data_turno ON medicao_sobra(data, turno);
CREATE INDEX idx_medicao_sobra_receita ON medicao_sobra(receita_id);

-- Itens do cardapio passam a carregar receitaId junto do nome. As linhas existentes ficam sem id
-- ate serem regravadas; o CardapioService resolve pelo nome no cadastro e recusa nome fora do
-- catalogo, entao nao ha mais casamento aproximado em lugar nenhum.
