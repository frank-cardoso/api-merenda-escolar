package br.com.frankcardoso.merenda.inteligencia.application.port;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AnaliseLogisticaInput(
    LocalDate data,
    Turno turno,
    String cardapio,
    int quantidadePlanejada,
    long consumosAutorizados,
    long tentativasBloqueadas,
    BigDecimal taxaConsumoPlanejado,
    long sobraEstimada
) {
}
