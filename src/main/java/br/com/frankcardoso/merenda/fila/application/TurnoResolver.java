package br.com.frankcardoso.merenda.fila.application;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.time.LocalTime;
import org.springframework.stereotype.Component;

@Component
public class TurnoResolver {

    public Turno resolver(LocalTime horario) {
        if (horario.isBefore(LocalTime.NOON)) return Turno.MANHA;
        if (horario.isBefore(LocalTime.of(18, 0))) return Turno.TARDE;
        if (horario.isBefore(LocalTime.of(23, 0))) return Turno.NOITE;
        throw new IllegalStateException("FORA_DO_TURNO");
    }
}
