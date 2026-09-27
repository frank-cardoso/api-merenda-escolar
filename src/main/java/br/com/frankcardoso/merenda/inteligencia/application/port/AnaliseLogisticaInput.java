package br.com.frankcardoso.merenda.inteligencia.application.port;

import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoOutput;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.historico.api.ItemHistorico;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dados agregados enviados ao modelo.
 *
 * {@code taxaConsumoPlanejado} e {@code sobraDePlanejamento} sao derivaveis dos outros campos, mas
 * vao calculados de proposito: modelo de linguagem erra aritmetica, e mandar pronto evita conta
 * errada na saida. {@code sobraDePlanejamento} tem esse nome porque nao e desperdicio: refeicao
 * planejada e nao registrada pode simplesmente nao ter sido preparada.
 *
 * {@code itensDoCardapioComTendencia} e o que permite a analise ir alem do obvio: taxa historica
 * de execucao (Java) e tendencia ao longo do tempo (Python) por item do cardapio de hoje, ja
 * mastigadas — o modelo nao recebe a serie dia a dia bruta.
 *
 * {@code conclusaoDeterministica} fecha o raciocinio numerico antes do modelo ver o payload. Sem
 * ele o modelo tentava deduzir direcao de ajuste sozinho e errava a proporcao inversa (achar que
 * aumentar o planejamento faria a taxa de execucao subir).
 *
 * {@code indicadores} e a projecao para o prompt, nao a fotografia completa: veja
 * {@link IndicadoresParaLLM}. O objeto inteiro continua indo para o banco, para a tela.
 */
public record AnaliseLogisticaInput(
    LocalDate data,
    Turno turno,
    String cardapio,
    List<String> itensDoCardapio,
    List<String> itensSelecionados,
    int quantidadePlanejada,
    long consumosAutorizados,
    long tentativasBloqueadas,
    BigDecimal taxaConsumoPlanejado,
    long sobraDePlanejamento,
    PrevisaoConsumoOutput previsaoConsumo,
    String avisoPrevisaoConsumo,
    List<ItemHistorico> itensComMenorExecucao,
    List<ItemComTendencia> itensDoCardapioComTendencia,
    IndicadoresParaLLM indicadores,
    ConclusaoDeterministica conclusaoDeterministica
) {

    public AnaliseLogisticaInput(
        LocalDate data,
        Turno turno,
        String cardapio,
        int quantidadePlanejada,
        long consumosAutorizados,
        long tentativasBloqueadas,
        BigDecimal taxaConsumoPlanejado,
        long sobraDePlanejamento
    ) {
        this(
            data,
            turno,
            cardapio,
            List.of(),
            List.of(),
            quantidadePlanejada,
            consumosAutorizados,
            tentativasBloqueadas,
            taxaConsumoPlanejado,
            sobraDePlanejamento,
            null,
            null,
            List.of(),
            List.of(),
            null,
            ConclusaoDeterministica.de(null, null)
        );
    }
}
