package br.com.frankcardoso.merenda.fila.api;

import br.com.frankcardoso.merenda.fila.domain.MetodoIdentificacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ValidarConsumoRequest(
    @NotBlank String alunoCodigo,
    @NotNull MetodoIdentificacao metodoIdentificacao
) {
}
