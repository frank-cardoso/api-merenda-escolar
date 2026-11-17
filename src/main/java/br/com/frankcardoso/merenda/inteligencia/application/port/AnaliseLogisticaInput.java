package br.com.frankcardoso.merenda.inteligencia.application.port;

import br.com.frankcardoso.merenda.historico.api.ItemHistorico;
import java.time.LocalDate;
import java.util.List;

/**
 * Dados agregados enviados ao modelo.
 *
 * So o que vale para o periodo analisado (ultimos 30 dias). Contagens do dia de referencia
 * (planejado, autorizados, bloqueados, taxa, sobra, previsao de demanda) ficam de fora: com a
 * refeicao ainda em andamento o modelo as citava como se fossem o periodo e contradizia o cartao
 * de refeicoes servidas da tela.
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
    String refeicao,
    String cardapio,
    List<String> itensDoCardapio,
    List<String> itensSelecionados,
    List<ItemHistorico> itensComMenorExecucao,
    List<ItemComTendencia> itensDoCardapioComTendencia,
    IndicadoresParaLLM indicadores,
    ConclusaoDeterministica conclusaoDeterministica
) {

    public AnaliseLogisticaInput(LocalDate data, String refeicao, String cardapio) {
        this(data, refeicao, cardapio, List.of(), List.of(), List.of(), List.of(), null,
            ConclusaoDeterministica.de(null, null));
    }
}
