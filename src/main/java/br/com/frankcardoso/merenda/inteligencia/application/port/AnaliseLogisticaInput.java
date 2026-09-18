package br.com.frankcardoso.merenda.inteligencia.application.port;

import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoOutput;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.historico.api.ItemHistorico;
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
 * {@code itensDoCardapioComTendencia} e o que permite a analise ir alem do obvio: taxa historica
 * de execucao (Java) e tendencia ao longo do tempo (Python) por item do cardapio de hoje, ja
 * mastigadas — o modelo nao recebe a serie dia a dia bruta.
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
    List<ItemHistorico> itensComMenorExecucao,
    List<ItemComTendencia> itensDoCardapioComTendencia,
    IndicadoresLogisticos indicadores
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
            List.of(),
            List.of(),
            null
        );
    }
}
