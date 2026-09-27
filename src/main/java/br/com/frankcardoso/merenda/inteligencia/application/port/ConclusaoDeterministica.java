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
        "Aceitacao alimentar nao e avaliavel: nao ha medicao de resto no prato no periodo analisado.";
    private static final String LIMITE_ASSOCIACAO =
        "Ingrediente acima dos pratos sem ele e associacao observada, nao causa comprovada: o resto "
            + "e medido por prato inteiro, entao ingredientes servidos juntos dividem o mesmo "
            + "numero. A comparacao atenua essa heranca, nao a elimina.";
    private static final String LIMITE_DIRECAO =
        "Nao ha base para recomendar aumento ou reducao de quantidades planejadas: a diferenca "
            + "entre planejado e registrado nao indica a direcao do ajuste.";
    private static final String LIMITE_RANKING =
        "O ranking de itens e uma foto do periodo; posicao no ranking nao demonstra queda ao "
            + "longo do tempo.";
    // ingredientes e rotacaoCardapio nao entram aqui: os motivos deles ja viajam dentro do bloco
    // indicadores do mesmo payload, e o prompt ja proibe concluir sobre os dois.
    private static final String LIMITE_DESPERDICIO_PADRAO =
        "Desperdicio nao e avaliavel sem medicao de sobra nao distribuida e de resto no prato.";

    public static ConclusaoDeterministica de(
        IndicadoresLogisticos indicadores, PrevisaoConsumoOutput previsao
    ) {
        var afirmacoes = new ArrayList<String>();
        afirmacoes.addAll(afirmacoesDeExecucao(indicadores));
        afirmacoes.addAll(afirmacoesDePrevisao(previsao));

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
        if (indicadores == null || indicadores.execucaoPlanejamento() == null) {
            return List.of("Os indicadores nao ficaram disponiveis; nao ha percentual de execucao "
                + "nem avaliacao de meta para este periodo.");
        }

        var execucao = indicadores.execucaoPlanejamento();
        if (execucao.percentual() == null) {
            return List.of("Foram registrados %d consumos, mas sem planejamento informado nao ha "
                + "percentual de execucao nem avaliacao de meta.".formatted(
                    execucao.consumosRegistrados()));
        }

        var afirmacoes = new ArrayList<String>();
        afirmacoes.add("Os registros correspondem a %s%% do planejamento (%d de %d refeicoes)."
            .formatted(percentual(execucao.percentual()), execucao.consumosRegistrados(),
                execucao.refeicoesPlanejadas()));
        afirmacoes.add(afirmacaoDeMeta(execucao));

        if (indicadores.atendimentos() != null) {
            afirmacoes.add("Os registros vieram de %d alunos unicos, com %d repeticoes.".formatted(
                indicadores.atendimentos().alunosUnicos(), indicadores.atendimentos().repeticoes()));
        }
        return afirmacoes;
    }

    private static String afirmacaoDeMeta(IndicadoresLogisticos.ExecucaoPlanejamento execucao) {
        var meta = percentual(execucao.metaPercentual());
        var diferenca = execucao.diferencaMetaPp() == null
            ? null : percentual(execucao.diferencaMetaPp().abs());
        return switch (execucao.statusMeta()) {
            case "ATINGIDA" -> "A meta interna de execucao de %s%% foi atingida, %s pontos "
                .formatted(meta, diferenca) + "percentuais acima.";
            case "ABAIXO" -> "A meta interna de execucao de %s%% nao foi atingida, %s pontos "
                .formatted(meta, diferenca) + "percentuais abaixo.";
            default -> "A meta interna de execucao de %s%% nao e avaliavel neste periodo."
                .formatted(meta);
        };
    }

    // A media historica cobre dias inteiros; o dia corrente ja tem catraca rodada. Prever abaixo
    // do que ja aconteceu e impossivel, entao o realizado entra como piso.
    private static List<String> afirmacoesDePrevisao(PrevisaoConsumoOutput previsao) {
        if (previsao == null) {
            return List.of("O servico de previsao nao respondeu; nao ha estimativa de demanda.");
        }
        return List.of(switch (previsao.origemEstimativa() == null ? "" : previsao.origemEstimativa()) {
            case "PISO_REALIZADO" -> ("A estimativa de demanda e %d, limitada pelo realizado do dia; "
                + "a media historica ficou em %d.").formatted(
                    previsao.demandaEstimada(), previsao.mediaHistorica());
            case "MEDIA_HISTORICA" -> ("A estimativa de demanda e %d, pela media historica; o "
                + "realizado do dia ate agora e %d.").formatted(
                    previsao.demandaEstimada(), previsao.pisoRealizado());
            default -> ("Sem historico comparavel, a estimativa de demanda e o proprio realizado "
                + "do dia (%d).").formatted(previsao.demandaEstimada());
        });
    }

    private static boolean disponivel(String status) {
        return "DISPONIVEL".equals(status);
    }

    private static List<String> afirmacoesDeMedicao(
        IndicadoresLogisticos.Aceitacao aceitacao, IndicadoresLogisticos.Desperdicio desperdicio
    ) {
        var afirmacoes = new ArrayList<String>();
        if (disponivel(aceitacao == null ? null : aceitacao.status())) {
            afirmacoes.add(("A aceitacao medida no periodo foi de %s%% (%d porcoes servidas, %d de resto no "
                + "prato), nivel %s.").formatted(percentual(aceitacao.percentual()),
                    aceitacao.porcoesServidas(), aceitacao.restoNoPrato(), aceitacao.nivel()));
        }
        if (disponivel(desperdicio == null ? null : desperdicio.status())) {
            afirmacoes.add(("O desperdicio medido no periodo foi de %s%% do preparado (%d de sobra na cuba e "
                + "%d de resto no prato sobre %d porcoes preparadas), risco %s.").formatted(
                    percentual(desperdicio.percentual()), desperdicio.sobraNaoDistribuida(),
                    desperdicio.restoNoPrato(), desperdicio.porcoesPreparadas(),
                    desperdicio.risco()));
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
            return List.of("Nenhum ingrediente teve resto acima dos pratos sem ele. A media de "
                + "resto da janela e %s%%.".formatted(percentual(analise.basePercentualResto())));
        }
        // Tres bastam para a frase; o ranking inteiro segue no bloco de indicadores.
        var destaques = analise.acimaDaBase().stream().limit(3)
            .map(i -> "%s %s%% com contra %s%% sem (%s pp, n=%d)".formatted(i.ingrediente(),
                percentual(i.percentualRestoCom()), percentual(i.percentualRestoSem()),
                percentual(i.diferencaPp()), i.amostra()))
            .toList();
        return List.of("Resto maior nos pratos que levam o ingrediente do que nos que nao levam "
            + "(media da janela %s%%): %s.".formatted(
                percentual(analise.basePercentualResto()), String.join("; ", destaques)));
    }

    private static String percentual(BigDecimal valor) {
        return valor == null ? "-" : String.format(PT_BR, "%.2f", valor);
    }
}
