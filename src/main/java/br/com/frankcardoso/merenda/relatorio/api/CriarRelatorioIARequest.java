package br.com.frankcardoso.merenda.relatorio.api;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CriarRelatorioIARequest(
    @NotNull LocalDate dataReferencia,
    @NotNull Turno turno,
    List<UUID> receitaIds,
    List<LocalDate> datasSelecionadas
) {
    public CriarRelatorioIARequest {
        receitaIds = receitaIds == null ? List.of() : List.copyOf(receitaIds);
        datasSelecionadas = datasSelecionadas == null ? List.of() : List.copyOf(datasSelecionadas);
    }
}
