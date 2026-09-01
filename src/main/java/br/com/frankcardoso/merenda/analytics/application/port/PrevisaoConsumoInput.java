package br.com.frankcardoso.merenda.analytics.application.port;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PrevisaoConsumoInput(
    LocalDate dataReferencia,
    Turno turno,
    String cardapio,
    int quantidadePlanejada,
    long consumosAutorizados,
    long tentativasBloqueadas,
    BigDecimal taxaConsumoPlanejado,
    long sobraEstimada
) {
}
