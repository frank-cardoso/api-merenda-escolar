package br.com.frankcardoso.merenda.analytics.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * O servico de previsao ja usa {@code historico} para estimar a demanda pela media dos dias
 * anteriores e para classificar a confianca. Sem esse campo preenchido ele devolve o consumo do
 * proprio dia como previsao — circular — e sempre reporta confianca BAIXA com a evidencia
 * "historico insuficiente", que contradiz o historico enviado no prompt da analise.
 */
public record PrevisaoConsumoInput(
    LocalDate dataReferencia,
    String refeicao,
    String cardapio,
    int quantidadePlanejada,
    long consumosAutorizados,
    long tentativasBloqueadas,
    BigDecimal taxaConsumoPlanejado,
    long sobraDePlanejamento,
    List<DiaConsumo> historico
) {

    public PrevisaoConsumoInput(
        LocalDate dataReferencia,
        String refeicao,
        String cardapio,
        int quantidadePlanejada,
        long consumosAutorizados,
        long tentativasBloqueadas,
        BigDecimal taxaConsumoPlanejado,
        long sobraDePlanejamento
    ) {
        this(dataReferencia, refeicao, cardapio, quantidadePlanejada, consumosAutorizados,
            tentativasBloqueadas, taxaConsumoPlanejado, sobraDePlanejamento, List.of());
    }

    public record DiaConsumo(LocalDate dataReferencia, long consumosAutorizados) {
    }
}
