package br.com.frankcardoso.merenda.analytics.application.port;

import java.util.List;

/**
 * {@code mediaHistorica} e {@code pisoRealizado} vem separados de proposito: a previsao nunca
 * pode ficar abaixo do que a catraca ja registrou hoje, e sem os dois campos nao da para explicar
 * qual dos dois prevaleceu.
 *
 * {@code riscoDesperdicio} e NAO_AVALIAVEL enquanto nao houver medicao de sobra nao distribuida e
 * de resto no prato. Diferenca entre planejado e registrado e sobra de planejamento, nao comida
 * jogada fora.
 */
public record PrevisaoConsumoOutput(
    long demandaEstimada,
    Long mediaHistorica,
    long pisoRealizado,
    String origemEstimativa,
    long diferencaPrevisaoPlanejamento,
    String riscoDesperdicio,
    String desperdicioMotivo,
    String confianca,
    String metodo,
    List<String> evidencias
) {
}
