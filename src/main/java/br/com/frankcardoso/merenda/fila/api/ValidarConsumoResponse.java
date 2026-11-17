package br.com.frankcardoso.merenda.fila.api;

import br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ValidarConsumoResponse(
    ResultadoConsumo resultado,
    String sinal,
    UUID alunoId,
    String alunoNome,
    UUID tipoRefeicaoId,
    String refeicao,
    String cardapio,
    Instant registradoEm,
    String motivo,
    String turnoDaTurma,
    List<String> turnosDaRefeicao
) {
    public static ValidarConsumoResponse bloqueado(String motivo) {
        return new ValidarConsumoResponse(ResultadoConsumo.BLOQUEADO, "VERMELHO", null, null,
            null, null, null, null, motivo, null, null);
    }
}
