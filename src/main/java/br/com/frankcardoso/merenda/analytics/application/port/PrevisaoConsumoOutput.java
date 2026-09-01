package br.com.frankcardoso.merenda.analytics.application.port;

import java.util.List;

public record PrevisaoConsumoOutput(
    long demandaEstimada,
    long ajusteSugerido,
    String riscoDesperdicio,
    String confianca,
    String metodo,
    List<String> evidencias
) {
}
