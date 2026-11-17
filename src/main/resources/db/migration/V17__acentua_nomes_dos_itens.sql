-- Acentua os nomes de receitas e ingredientes que vieram do faker sem acento ("Maca", "Pao com
-- manteiga"). Os ids nao mudam: o uuid5 do gerador continua derivado do nome sem acento.
--
-- Base nova: as tabelas ainda estao vazias aqui e o CatalogoLoader ja carrega os TSV acentuados.
-- Base existente: o loader so carrega catalogo vazio, entao e esta migration que renomeia.
--
-- O cardapio guarda o nome do item duas vezes alem do id: em itens_json e na lista de nome_refeicao
-- que o seed de demonstracao monta. Os dois precisam acompanhar a receita, senao o seed ve nome
-- divergente do catalogo e sorteia outro cardapio para o dia. Em nome_refeicao o item so troca
-- quando aparece inteiro entre virgulas, para "Maca" nao pegar o inicio de "Macarrao ao sugo".
--
-- relatorio_ia fica como esta: e registro do que a IA recebeu e respondeu na epoca.

UPDATE receita SET nome = CASE nome
    WHEN 'Pure de mandioca' THEN 'Purê de mandioca'
    WHEN 'Molho de carne moida' THEN 'Molho de carne moída'
    WHEN 'Pure de batata' THEN 'Purê de batata'
    WHEN 'Carne moida tipo pastel' THEN 'Carne moída tipo pastel'
    WHEN 'Brocolis' THEN 'Brócolis'
    WHEN 'Requeijao caseiro' THEN 'Requeijão caseiro'
    WHEN 'Couve flor' THEN 'Couve-flor'
    WHEN 'Carne suina acebolada' THEN 'Carne suína acebolada'
    WHEN 'Pure de moranga' THEN 'Purê de moranga'
    WHEN 'Bolo de maca, sem acucar' THEN 'Bolo de maçã, sem açúcar'
    WHEN 'Feijao carioca' THEN 'Feijão carioca'
    WHEN 'Pao com manteiga' THEN 'Pão com manteiga'
    WHEN 'Maca' THEN 'Maçã'
    WHEN 'Macarrao ao sugo' THEN 'Macarrão ao sugo'
    ELSE nome END;

UPDATE ingrediente SET nome = CASE nome
    WHEN 'brocolis' THEN 'brócolis'
    WHEN 'couve flor' THEN 'couve-flor'
    WHEN 'fuba' THEN 'fubá'
    WHEN 'carne suina' THEN 'carne suína'
    WHEN 'maca' THEN 'maçã'
    WHEN 'feijao' THEN 'feijão'
    WHEN 'acucar' THEN 'açúcar'
    ELSE nome END;

-- 'g': no MODE=PostgreSQL o H2 so troca a primeira ocorrencia sem essa flag.
UPDATE cardapio SET itens_json = REGEXP_REPLACE(itens_json, '"nome"\s*:\s*"Pure de mandioca"', '"nome":"Purê de mandioca"', 'g');
UPDATE cardapio SET itens_json = REGEXP_REPLACE(itens_json, '"nome"\s*:\s*"Molho de carne moida"', '"nome":"Molho de carne moída"', 'g');
UPDATE cardapio SET itens_json = REGEXP_REPLACE(itens_json, '"nome"\s*:\s*"Pure de batata"', '"nome":"Purê de batata"', 'g');
UPDATE cardapio SET itens_json = REGEXP_REPLACE(itens_json, '"nome"\s*:\s*"Carne moida tipo pastel"', '"nome":"Carne moída tipo pastel"', 'g');
UPDATE cardapio SET itens_json = REGEXP_REPLACE(itens_json, '"nome"\s*:\s*"Brocolis"', '"nome":"Brócolis"', 'g');
UPDATE cardapio SET itens_json = REGEXP_REPLACE(itens_json, '"nome"\s*:\s*"Requeijao caseiro"', '"nome":"Requeijão caseiro"', 'g');
UPDATE cardapio SET itens_json = REGEXP_REPLACE(itens_json, '"nome"\s*:\s*"Couve flor"', '"nome":"Couve-flor"', 'g');
UPDATE cardapio SET itens_json = REGEXP_REPLACE(itens_json, '"nome"\s*:\s*"Carne suina acebolada"', '"nome":"Carne suína acebolada"', 'g');
UPDATE cardapio SET itens_json = REGEXP_REPLACE(itens_json, '"nome"\s*:\s*"Pure de moranga"', '"nome":"Purê de moranga"', 'g');
UPDATE cardapio SET itens_json = REGEXP_REPLACE(itens_json, '"nome"\s*:\s*"Bolo de maca, sem acucar"', '"nome":"Bolo de maçã, sem açúcar"', 'g');
UPDATE cardapio SET itens_json = REGEXP_REPLACE(itens_json, '"nome"\s*:\s*"Feijao carioca"', '"nome":"Feijão carioca"', 'g');
UPDATE cardapio SET itens_json = REGEXP_REPLACE(itens_json, '"nome"\s*:\s*"Pao com manteiga"', '"nome":"Pão com manteiga"', 'g');
UPDATE cardapio SET itens_json = REGEXP_REPLACE(itens_json, '"nome"\s*:\s*"Maca"', '"nome":"Maçã"', 'g');
UPDATE cardapio SET itens_json = REGEXP_REPLACE(itens_json, '"nome"\s*:\s*"Macarrao ao sugo"', '"nome":"Macarrão ao sugo"', 'g');

UPDATE cardapio SET nome_refeicao = REGEXP_REPLACE(nome_refeicao, '(?<=^|, )Pure de mandioca(?=, |$)', 'Purê de mandioca', 'g');
UPDATE cardapio SET nome_refeicao = REGEXP_REPLACE(nome_refeicao, '(?<=^|, )Molho de carne moida(?=, |$)', 'Molho de carne moída', 'g');
UPDATE cardapio SET nome_refeicao = REGEXP_REPLACE(nome_refeicao, '(?<=^|, )Pure de batata(?=, |$)', 'Purê de batata', 'g');
UPDATE cardapio SET nome_refeicao = REGEXP_REPLACE(nome_refeicao, '(?<=^|, )Carne moida tipo pastel(?=, |$)', 'Carne moída tipo pastel', 'g');
UPDATE cardapio SET nome_refeicao = REGEXP_REPLACE(nome_refeicao, '(?<=^|, )Brocolis(?=, |$)', 'Brócolis', 'g');
UPDATE cardapio SET nome_refeicao = REGEXP_REPLACE(nome_refeicao, '(?<=^|, )Requeijao caseiro(?=, |$)', 'Requeijão caseiro', 'g');
UPDATE cardapio SET nome_refeicao = REGEXP_REPLACE(nome_refeicao, '(?<=^|, )Couve flor(?=, |$)', 'Couve-flor', 'g');
UPDATE cardapio SET nome_refeicao = REGEXP_REPLACE(nome_refeicao, '(?<=^|, )Carne suina acebolada(?=, |$)', 'Carne suína acebolada', 'g');
UPDATE cardapio SET nome_refeicao = REGEXP_REPLACE(nome_refeicao, '(?<=^|, )Pure de moranga(?=, |$)', 'Purê de moranga', 'g');
UPDATE cardapio SET nome_refeicao = REGEXP_REPLACE(nome_refeicao, '(?<=^|, )Bolo de maca, sem acucar(?=, |$)', 'Bolo de maçã, sem açúcar', 'g');
UPDATE cardapio SET nome_refeicao = REGEXP_REPLACE(nome_refeicao, '(?<=^|, )Feijao carioca(?=, |$)', 'Feijão carioca', 'g');
UPDATE cardapio SET nome_refeicao = REGEXP_REPLACE(nome_refeicao, '(?<=^|, )Pao com manteiga(?=, |$)', 'Pão com manteiga', 'g');
UPDATE cardapio SET nome_refeicao = REGEXP_REPLACE(nome_refeicao, '(?<=^|, )Maca(?=, |$)', 'Maçã', 'g');
UPDATE cardapio SET nome_refeicao = REGEXP_REPLACE(nome_refeicao, '(?<=^|, )Macarrao ao sugo(?=, |$)', 'Macarrão ao sugo', 'g');

-- Rotulo do cardapio padrao do seed, usado quando o catalogo nao carrega.
UPDATE cardapio SET nome_refeicao = 'Arroz e feijão'
WHERE nome_refeicao = 'Arroz e feijao' AND descricao = 'Cardapio de demonstracao';
