-- Soft delete de cardapio: a linha nunca e removida, apenas marcada com ativo = FALSE.
-- Isso preserva a integridade de auditoria_consumo.cardapio_id, que aponta para o
-- cardapio efetivamente servido.
--
-- A constraint UNIQUE(data, turno) precisa sair: com soft delete, varias linhas
-- historicas (inativas) podem existir para a mesma data e turno. O ideal seria um
-- indice unico parcial (UNIQUE ... WHERE ativo = TRUE), mas o H2 nao suporta essa
-- sintaxe, entao a regra "apenas um cardapio ativo por data e turno" passa a ser
-- garantida pelo CardapioService.
ALTER TABLE cardapio DROP CONSTRAINT uk_cardapio_data_turno;

CREATE INDEX idx_cardapio_data_turno_ativo ON cardapio(data, turno, ativo);

-- itens_json passa a guardar objetos ({"nome": ..., "quantidade": ...}) em vez de strings
-- soltas, porque o cadastro agora tem quantidade por item. Converte as linhas ja existentes
-- no formato antigo: ["arroz","feijao"] -> [{"nome":"arroz"},{"nome":"feijao"}].
UPDATE cardapio
SET itens_json = REPLACE(
        REPLACE(
            REPLACE(CAST(itens_json AS VARCHAR), '","', '"},{"nome":"'),
            '["', '[{"nome":"'),
        '"]', '"}]')
WHERE CAST(itens_json AS VARCHAR) LIKE '["%';
