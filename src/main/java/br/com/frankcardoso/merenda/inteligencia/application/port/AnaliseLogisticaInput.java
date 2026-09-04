package br.com.frankcardoso.merenda.inteligencia.application.port;

import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoOutput;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.historico.api.HistoricoConsumoResumo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dados agregados enviados ao modelo.
 *
 * {@code taxaConsumoPlanejado} e {@code sobraEstimada} sao derivaveis dos outros campos, mas vao
 * calculados de proposito: modelo de linguagem erra aritmetica, e mandar pronto evita conta
 * errada na saida.
 *
 * {@code historico} e o que permite a analise ir alem do obvio. Sem ele o modelo recebe um unico
 * ponto de dado e so consegue reafirmar o que ja esta no proprio payload.
 */
public record AnaliseLogisticaInput(
    LocalDate data,
    Turno turno,
    String cardapio,
    List<String> itensDoCardapio,
    int quantidadePlanejada,
    long consumosAutorizados,
    long tentativasBloqueadas,
    BigDecimal taxaConsumoPlanejado,
    long sobraEstimada,
    PrevisaoConsumoOutput previsaoConsumo,
    String avisoPrevisaoConsumo,
    HistoricoConsumoResumo historico
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
            List.of(),
            quantidadePlanejada,
            consumosAutorizados,
            tentativasBloqueadas,
            taxaConsumoPlanejado,
            sobraEstimada,
            null,
            null,
            HistoricoConsumoResumo.vazio()
        );
    }
}
