package br.com.frankcardoso.merenda.fila.api;

import br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.time.Instant;
import java.util.UUID;

public record ValidarConsumoResponse(
    ResultadoConsumo resultado,
    String sinal,
    UUID alunoId,
    String alunoNome,
    Turno turno,
    String cardapio,
    Instant registradoEm,
    String motivo
) {
    public static ValidarConsumoResponse bloqueado(String motivo, Turno turno) {
        return new ValidarConsumoResponse(ResultadoConsumo.BLOQUEADO, "VERMELHO", null, null,
            turno, null, null, motivo);
    }
}
