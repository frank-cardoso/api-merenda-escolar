package br.com.frankcardoso.merenda.relatorio.api;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CriarRelatorioIARequest(
    @NotNull LocalDate dataReferencia,
    @NotNull Turno turno
) {
}
