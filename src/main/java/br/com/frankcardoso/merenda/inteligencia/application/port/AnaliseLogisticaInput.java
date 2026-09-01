package br.com.frankcardoso.merenda.inteligencia.application.port;

import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoOutput;
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
    long sobraEstimada,
    PrevisaoConsumoOutput previsaoConsumo,
    String avisoPrevisaoConsumo
) {

    public AnaliseLogisticaInput(
        LocalDate data,
        Turno turno,
        String cardapio,
        int quantidadePlanejada,
        long consumosAutorizados,
        long tentativasBloqueadas,
        BigDecimal taxaConsumoPlanejado,
        long sobraEstimada
    ) {
        this(
            data,
            turno,
            cardapio,
            quantidadePlanejada,
            consumosAutorizados,
            tentativasBloqueadas,
            taxaConsumoPlanejado,
            sobraEstimada,
            null,
            null
        );
    }
}
