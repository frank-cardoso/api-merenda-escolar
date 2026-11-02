package br.com.frankcardoso.merenda.aluno.api;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record TransferenciaRequest(@NotNull UUID turmaId, @NotNull LocalDate dataMudanca) {
}
