package br.com.frankcardoso.merenda.inteligencia.application.port;

import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.Aceitacao;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.AnaliseIndisponivel;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.AnaliseIngredientes;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.Desperdicio;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.Atendimentos;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.ExecucaoPlanejamento;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.ItemRanking;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.TurmaIndicadores;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Projecao de {@link IndicadoresLogisticos} para o prompt. O objeto original continua inteiro no
 * {@code indicadores_json} do relatorio, que e o que a tela de Indicadores le linha a linha.
 *
 * Existe porque os dois consumidores querem coisas diferentes: a tela precisa do detalhe por
 * turma, o modelo precisa do panorama. Enquanto nao houver denominador de presenca, toda turma sai
 * NAO_AVALIAVEL com o mesmo motivo, e mandar isso repetido era o maior item do payload (1.918 de
 * 8.775 caracteres) sobre um bloco que o proprio prompt proibe o modelo de concluir.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IndicadoresParaLLM(
    String schemaVersion, String calculoVersao, String status,
    LocalDate dataReferencia, LocalDate inicioHistorico, Turno turno,
    ExecucaoPlanejamento execucaoPlanejamento, Atendimentos atendimentos,
    List<ItemRanking> topComidas, List<TurmaIndicadores> porTurma, ResumoTurmas resumoTurmas,
    Aceitacao aceitacao, Desperdicio desperdicio,
    AnaliseIngredientes ingredientes, AnaliseIndisponivel rotacaoCardapio, List<String> avisos
) {

    private static final String NAO_AVALIAVEL = "NAO_AVALIAVEL";

    /** Contagens somadas das turmas, com os motivos distintos uma vez cada. */
    public record ResumoTurmas(int turmas, String statusMeta, long consumosRegistrados,
        long alunosUnicos, long repeticoes, List<String> motivos) {}

    public static IndicadoresParaLLM de(IndicadoresLogisticos origem) {
        if (origem == null) return null;

        var turmas = origem.porTurma() == null ? List.<TurmaIndicadores>of() : origem.porTurma();
        // O detalhe so vale a pena quando alguma turma tem percentual proprio para comparar.
        var resumir = !turmas.isEmpty()
            && turmas.stream().allMatch(turma -> NAO_AVALIAVEL.equals(turma.statusMeta()));

        return new IndicadoresParaLLM(
            origem.schemaVersion(), origem.calculoVersao(), origem.status(),
            origem.dataReferencia(), origem.inicioHistorico(), origem.turno(),
            origem.execucaoPlanejamento(), origem.atendimentos(), origem.topComidas(),
            resumir ? null : turmas, resumir ? resumir(turmas) : null,
            origem.aceitacao(), origem.desperdicio(),
            origem.ingredientes(), origem.rotacaoCardapio(), origem.avisos()
        );
    }

    private static ResumoTurmas resumir(List<TurmaIndicadores> turmas) {
        var motivos = new LinkedHashSet<String>();
        turmas.stream().map(TurmaIndicadores::motivo).filter(Objects::nonNull)
            .forEach(motivos::add);

        return new ResumoTurmas(
            turmas.size(),
            NAO_AVALIAVEL,
            turmas.stream().mapToLong(TurmaIndicadores::consumosRegistrados).sum(),
            turmas.stream().mapToLong(TurmaIndicadores::alunosUnicos).sum(),
            turmas.stream().mapToLong(TurmaIndicadores::repeticoes).sum(),
            List.copyOf(motivos)
        );
    }
}
