package br.com.frankcardoso.merenda.refeicao.api;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.Set;

public record TipoRefeicaoRequest(
    @NotBlank @Size(max = 60) String nome,
    @NotNull @JsonFormat(pattern = "HH:mm") LocalTime inicio,
    @NotNull @JsonFormat(pattern = "HH:mm") LocalTime fim,
    @NotNull Set<@NotNull Turno> turnos) {
}
