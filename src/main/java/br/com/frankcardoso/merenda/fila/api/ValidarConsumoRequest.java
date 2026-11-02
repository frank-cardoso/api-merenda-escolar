package br.com.frankcardoso.merenda.fila.api;

import br.com.frankcardoso.merenda.fila.domain.MetodoIdentificacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code fotoBase64} so e exigido quando {@code metodoIdentificacao} e {@code FACIAL}: o
 * operador ja sabe quem e o aluno (selecionou na lista), a foto so confirma 1:1 contra o
 * rosto cadastrado dele. Nao ha busca entre alunos.
 */
public record ValidarConsumoRequest(
    @NotBlank String alunoCodigo,
    @NotNull MetodoIdentificacao metodoIdentificacao,
    String fotoBase64
) {
}
