package br.com.frankcardoso.merenda.inteligencia.application.port;

import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.AcaoPlano;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.Aceitacao;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.AnaliseIndisponivel;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.AnaliseIngredientes;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.Desperdicio;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.ItemRanking;
import br.com.frankcardoso.merenda.analytics.application.port.RefeicaoRef;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Projecao de {@link IndicadoresLogisticos} para o prompt. O objeto original continua inteiro no
 * {@code indicadores_json} do relatorio, que e o que a tela de Indicadores le linha a linha.
 *
 * Existe porque os dois consumidores querem coisas diferentes: a tela mostra o dia de referencia
 * e o detalhe por turma, o modelo so pode falar do periodo. Com a refeicao do dia em andamento,
 * contagens do dia (17 de 51, 33%) eram citadas como o periodo e contradiziam o cartao de
 * refeicoes servidas, que usa as medias da janela. Por isso ficam de fora a execucao do dia, os
 * atendimentos e as contagens por turma.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IndicadoresParaLLM(
    String schemaVersion, String calculoVersao, String status,
    LocalDate dataReferencia, LocalDate inicioHistorico, RefeicaoRef refeicao,
    ExecucaoJanela execucaoPlanejamento,
    List<ItemRanking> topComidas,
    Aceitacao aceitacao, Desperdicio desperdicio,
    AnaliseIngredientes ingredientes, AnaliseIndisponivel rotacaoCardapio, List<String> avisos,
    List<AcaoPlano> planoDeAcao
) {

    private static final String REVER_PLANEJAMENTO = "REVER_PLANEJAMENTO";

    /** Refeicoes servidas por dia na janela de analise; os mesmos numeros do cartao da tela. */
    public record ExecucaoJanela(BigDecimal mediaAtendidosDia, BigDecimal mediaPlanejadaDia,
        BigDecimal percentual) {

        /** {@code null} quando a janela nao tem as duas medias. */
        public static ExecucaoJanela de(IndicadoresLogisticos origem) {
            var execucao = origem == null ? null : origem.execucaoPlanejamento();
            if (execucao == null || execucao.mediaAtendidosDia() == null
                || execucao.mediaPlanejadaDia() == null) {
                return null;
            }
            var mediaPlanejada = execucao.mediaPlanejadaDia();
            var percentual = origem.planoDeAcao() == null ? null : origem.planoDeAcao().stream()
                .filter(acao -> REVER_PLANEJAMENTO.equals(acao.tipo()) && acao.valor() != null)
                .map(AcaoPlano::valor).findFirst().orElse(null);
            if (percentual == null && mediaPlanejada.signum() > 0) {
                percentual = execucao.mediaAtendidosDia().multiply(BigDecimal.valueOf(100))
                    .divide(mediaPlanejada, 1, RoundingMode.HALF_UP);
            }
            return new ExecucaoJanela(execucao.mediaAtendidosDia(), mediaPlanejada, percentual);
        }
    }

    public static IndicadoresParaLLM de(IndicadoresLogisticos origem) {
        return de(origem, null);
    }

    /**
     * Com {@code turmaId} a analise segue sendo da refeicao inteira: resto no prato nao e medido
     * por turma, entao nao ha como escopar aceitacao e ingredientes sem inventar dado. O aviso
     * evita o modelo tratar o resultado como da turma.
     */
    public static IndicadoresParaLLM de(IndicadoresLogisticos origem, UUID turmaId) {
        if (origem == null) return null;

        var avisos = new ArrayList<>(origem.avisos() == null ? List.<String>of() : origem.avisos());
        var turma = turmaId == null || origem.porTurma() == null ? null : origem.porTurma().stream()
            .filter(t -> turmaId.equals(t.turmaId())).findFirst().orElse(null);
        if (turma != null) {
            avisos.add("Filtro de turma ativo (" + turma.turma() + "): execução do planejamento, "
                + "aceitação e ingredientes são da refeição inteira, porque resto no prato não é "
                + "medido por turma.");
        }

        return new IndicadoresParaLLM(
            origem.schemaVersion(), origem.calculoVersao(), origem.status(),
            origem.dataReferencia(), origem.inicioHistorico(), origem.refeicao(),
            ExecucaoJanela.de(origem), origem.topComidas(),
            origem.aceitacao(), origem.desperdicio(),
            origem.ingredientes(), origem.rotacaoCardapio(), List.copyOf(avisos),
            origem.planoDeAcao()
        );
    }
}
