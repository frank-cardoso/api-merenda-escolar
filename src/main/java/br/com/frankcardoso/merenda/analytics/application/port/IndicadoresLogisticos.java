package br.com.frankcardoso.merenda.analytics.application.port;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Fotografia calculada, independente da interpretação de um modelo de linguagem. */
public record IndicadoresLogisticos(
    String schemaVersion, String calculoVersao, String status,
    LocalDate dataReferencia, LocalDate inicioHistorico, Turno turno,
    ExecucaoPlanejamento execucaoPlanejamento, Atendimentos atendimentos,
    List<ItemRanking> topComidas, List<TurmaIndicadores> porTurma,
    AnaliseIndisponivel ingredientes, AnaliseIndisponivel rotacaoCardapio, List<String> avisos
) {
    public record ExecucaoPlanejamento(long refeicoesPlanejadas, long consumosRegistrados,
        BigDecimal percentual, BigDecimal metaPercentual, BigDecimal diferencaMetaPp, String statusMeta) {}
    public record Atendimentos(long alunosUnicos, long repeticoes) {}
    public record ItemRanking(String item, long planejamentos, long execucoesRegistradas,
        int escolas, List<String> origens, String metrica, BigDecimal percentual) {}
    public record TurmaIndicadores(String turma, long consumosRegistrados, long alunosUnicos,
        long repeticoes, BigDecimal percentual, String statusMeta, String motivo) {}
    public record AnaliseIndisponivel(String status, String motivo, Integer cicloSugeridoDias) {}

    public static IndicadoresLogisticos indisponivel(LocalDate data, Turno turno) {
        return new IndicadoresLogisticos("2", "indicadores-v1", "INDISPONIVEL", data,
            data.minusDays(29), turno, null, null, List.of(), List.of(), null, null,
            List.of("Serviço de indicadores indisponível. Nenhum percentual foi estimado pela IA."));
    }
}
