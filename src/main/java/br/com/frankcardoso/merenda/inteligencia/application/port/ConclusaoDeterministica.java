package br.com.frankcardoso.merenda.inteligencia.application.port;

import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos;
import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoOutput;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Conclusoes ja fechadas pelo codigo deterministico. O modelo redige a partir daqui: nao
 * recalcula, nao classifica e nao escolhe direcao de ajuste.
 *
 * Existe no lugar de um exemplo fixo no prompt (few-shot). Texto de exemplo vira molde que o
 * modelo reaproveita em dia que nao se aplica — ja aconteceu neste projeto. Frase montada com os
 * numeros do proprio dia nao tem esse risco e ainda sai mais barata em token que um exemplo extra.
 */
public record ConclusaoDeterministica(
    String nivelAceitacao,
    String riscoDesperdicio,
    List<String> afirmacoes,
    List<String> limitacoes
) {

    private static final String NAO_AVALIAVEL = "NAO_AVALIAVEL";
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private static final String LIMITE_ACEITACAO =
        "A aceitação alimentar não é avaliável: não há medição de resto no prato no período analisado.";
    private static final String LIMITE_ASSOCIACAO =
        "Ingrediente com mais resto que os pratos sem ele é associação observada, não causa "
            + "comprovada: o resto é medido por prato inteiro, então ingredientes servidos juntos "
            + "dividem o mesmo número.";
    private static final String LIMITE_DIRECAO =
        "A diferença entre planejado e registrado, sozinha, não indica se a quantidade planejada "
            + "deve subir ou descer.";
    private static final String LIMITE_RANKING =
        "A ordem dos itens mostra o período analisado; não prova queda ou melhora ao longo do tempo.";
    // ingredientes e rotacaoCardapio nao entram aqui: os motivos deles ja viajam dentro do bloco
    // indicadores do mesmo payload, e o prompt ja proibe concluir sobre os dois.
    private static final String LIMITE_DESPERDICIO_PADRAO =
        "O desperdício não é avaliável sem medição da sobra na panela e do resto no prato.";

    public static ConclusaoDeterministica de(
        IndicadoresLogisticos indicadores, PrevisaoConsumoOutput previsao
    ) {
        var afirmacoes = new ArrayList<String>();
        afirmacoes.addAll(afirmacoesDeExecucao(indicadores));

        var aceitacao = indicadores == null ? null : indicadores.aceitacao();
        var desperdicio = indicadores == null ? null : indicadores.desperdicio();
        afirmacoes.addAll(afirmacoesDeMedicao(aceitacao, desperdicio));
        afirmacoes.addAll(afirmacoesDeIngredientes(indicadores));

        var limitacoes = new ArrayList<String>();
        if (!disponivel(aceitacao == null ? null : aceitacao.status())) {
            limitacoes.add(LIMITE_ACEITACAO);
        }
        if (!disponivel(desperdicio == null ? null : desperdicio.status())) {
            limitacoes.add(previsao == null || previsao.desperdicioMotivo() == null
                ? LIMITE_DESPERDICIO_PADRAO : previsao.desperdicioMotivo());
        }
        limitacoes.add(LIMITE_DIRECAO);
        limitacoes.add(LIMITE_RANKING);
        if (indicadores != null && indicadores.ingredientes() != null
            && !indicadores.ingredientes().acimaDaBase().isEmpty()) {
            limitacoes.add(LIMITE_ASSOCIACAO);
        }

        return new ConclusaoDeterministica(
            aceitacao == null || aceitacao.nivel() == null ? NAO_AVALIAVEL : aceitacao.nivel(),
            desperdicio == null || desperdicio.risco() == null ? NAO_AVALIAVEL : desperdicio.risco(),
            List.copyOf(afirmacoes), List.copyOf(limitacoes));
    }

    private static List<String> afirmacoesDeExecucao(IndicadoresLogisticos indicadores) {
        var janela = IndicadoresParaLLM.ExecucaoJanela.de(indicadores);
        if (janela == null) {
            return List.of("Os indicadores não ficaram disponíveis; não há média de refeições "
                + "servidas por dia nos últimos 30 dias.");
        }
        var sufixo = janela.percentual() == null ? "" : " (%s%%)".formatted(percentual(janela.percentual()));
        return List.of("Nos últimos 30 dias, foram servidas em média %s refeições por dia para %s planejadas%s."
            .formatted(percentual(janela.mediaAtendidosDia()), percentual(janela.mediaPlanejadaDia()), sufixo));
    }

    private static boolean disponivel(String status) {
        return "DISPONIVEL".equals(status);
    }

    private static List<String> afirmacoesDeMedicao(
        IndicadoresLogisticos.Aceitacao aceitacao, IndicadoresLogisticos.Desperdicio desperdicio
    ) {
        var afirmacoes = new ArrayList<String>();
        if (disponivel(aceitacao == null ? null : aceitacao.status())) {
            afirmacoes.add(("A aceitação medida no período foi de %s%% (%d porções servidas, %d de resto no "
                + "prato), nível %s.").formatted(percentual(aceitacao.percentual()),
                    aceitacao.porcoesServidas(), aceitacao.restoNoPrato(), porExtenso(aceitacao.nivel())));
        }
        if (disponivel(desperdicio == null ? null : desperdicio.status())) {
            afirmacoes.add(("O desperdício medido no período foi de %s%% do preparado (%d de sobra na panela e "
                + "%d de resto no prato sobre %d porções preparadas), risco %s.").formatted(
                    percentual(desperdicio.percentual()), desperdicio.sobraNaoDistribuida(),
                    desperdicio.restoNoPrato(), desperdicio.porcoesPreparadas(),
                    porExtenso(desperdicio.risco())));
        }
        return afirmacoes;
    }

    private static List<String> afirmacoesDeIngredientes(IndicadoresLogisticos indicadores) {
        if (indicadores == null || indicadores.ingredientes() == null
            || !disponivel(indicadores.ingredientes().status())) {
            return List.of();
        }
        var analise = indicadores.ingredientes();
        if (analise.acimaDaBase() == null || analise.acimaDaBase().isEmpty()) {
            return List.of("Nenhum ingrediente teve resto acima dos pratos sem ele. A média de "
                + "resto da janela é %s%%.".formatted(percentual(analise.basePercentualResto())));
        }
        // Tres bastam para a frase; o ranking inteiro segue no bloco de indicadores.
        var destaques = analise.acimaDaBase().stream().limit(3)
            .map(i -> "%s %s%% com contra %s%% sem (%s pp, n=%d)".formatted(i.ingrediente(),
                percentual(i.percentualRestoCom()), percentual(i.percentualRestoSem()),
                percentual(i.diferencaPp()), i.amostra()))
            .toList();
        return List.of("Resto maior nos pratos que levam o ingrediente do que nos que não levam "
            + "(média da janela %s%%): %s.".formatted(
                percentual(analise.basePercentualResto()), String.join("; ", destaques)));
    }

    private static String porExtenso(String classificacao) {
        if (classificacao == null) return "não avaliável";
        return switch (classificacao) {
            case "ALTA" -> "alta";
            case "MEDIA" -> "média";
            case "BAIXA" -> "baixa";
            case "ALTO" -> "alto";
            case "MEDIO" -> "médio";
            case "BAIXO" -> "baixo";
            default -> "não avaliável";
        };
    }

    /** Resumo de 2 frases montado só com os números prontos; é o texto do Fake e o plano B sem IA. */
    public static String resumoCurto(IndicadoresParaLLM indicadores) {
        var aceitacao = indicadores == null ? null : indicadores.aceitacao();
        var medido = aceitacao != null && disponivel(aceitacao.status());
        var frase = medido
            ? "A aceitação medida nos últimos 30 dias foi de %s%%.".formatted(percentual(aceitacao.percentual()))
            : "Ainda não há medição suficiente para avaliar a aceitação nos últimos 30 dias.";
        var plano = indicadores == null ? null : indicadores.planoDeAcao();
        var acao = plano != null && !plano.isEmpty()
            ? "Ação mais importante: " + plano.get(0).texto().replace(": ", " — ")
            : medido ? "Nenhuma ação necessária: todos os itens medidos estão dentro das metas."
            : "Ainda não há ações a recomendar: falta medição suficiente nos últimos 30 dias.";
        return frase + " " + acao;
    }

    public static String percentual(BigDecimal valor) {
        if (valor == null) return "-";
        var texto = String.format(PT_BR, "%.1f", valor);
        return texto.endsWith(",0") ? texto.substring(0, texto.length() - 2) : texto;
    }
}
