package br.com.frankcardoso.merenda.turma.api;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TurmaRequest(
    @NotBlank @Size(max = 40) String codigo,
    @NotBlank @Size(max = 150) String descricao,
    @NotNull Turno turno,
    @Positive Integer quantidadeMaximaAlunos
) {
}
