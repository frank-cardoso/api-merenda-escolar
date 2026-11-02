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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
        return de(origem, null);
    }

    /**
     * Com {@code turmaId}, o panorama de autorizacoes/alunos/repeticoes passa a ser o da turma
     * escolhida. Execucao do planejamento, aceitacao e ingredientes continuam do turno inteiro:
     * resto no prato nao e medido por turma, entao nao ha como escopar essa parte sem inventar
     * dado. Um aviso explicito evita o modelo tratar os dois panoramas como a mesma unidade.
     */
    public static IndicadoresParaLLM de(IndicadoresLogisticos origem, UUID turmaId) {
        if (origem == null) return null;

        var todasAsTurmas = origem.porTurma() == null ? List.<TurmaIndicadores>of() : origem.porTurma();
        var turmaEscolhida = turmaId == null ? null : todasAsTurmas.stream()
            .filter(turma -> turmaId.equals(turma.turmaId())).findFirst().orElse(null);

        var turmas = turmaEscolhida != null ? List.of(turmaEscolhida) : todasAsTurmas;
        // O detalhe so vale a pena quando alguma turma tem percentual proprio para comparar.
        var resumir = turmaEscolhida == null && !turmas.isEmpty()
            && turmas.stream().allMatch(turma -> NAO_AVALIAVEL.equals(turma.statusMeta()));

        var atendimentos = turmaEscolhida != null
            ? new Atendimentos(turmaEscolhida.alunosUnicos(), turmaEscolhida.repeticoes())
            : origem.atendimentos();

        var avisos = new ArrayList<>(origem.avisos() == null ? List.<String>of() : origem.avisos());
        if (turmaEscolhida != null) {
            avisos.add("Filtro de turma ativo (" + turmaEscolhida.turma() + "): autorizacoes, alunos "
                + "unicos e repeticoes sao so dessa turma. Execucao do planejamento, aceitacao e "
                + "ingredientes continuam do turno inteiro, porque resto no prato nao e medido por turma.");
        }

        return new IndicadoresParaLLM(
            origem.schemaVersion(), origem.calculoVersao(), origem.status(),
            origem.dataReferencia(), origem.inicioHistorico(), origem.turno(),
            origem.execucaoPlanejamento(), atendimentos, origem.topComidas(),
            resumir ? null : turmas, resumir ? resumir(turmas) : null,
            origem.aceitacao(), origem.desperdicio(),
            origem.ingredientes(), origem.rotacaoCardapio(), List.copyOf(avisos)
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
