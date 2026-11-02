-- Cadastro facial do aluno: so o vetor (embedding) gerado pelo servico Python, nunca a foto.
-- Null ate o aluno ter o rosto cadastrado; fila com metodo FACIAL exige essa coluna preenchida.
ALTER TABLE aluno ADD rosto_embedding_json CHARACTER LARGE OBJECT;
