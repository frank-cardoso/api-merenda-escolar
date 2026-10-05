package br.com.frankcardoso.merenda.analytics.application.port;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Fotografia calculada, independente da interpretação de um modelo de linguagem. */
public record IndicadoresLogisticos(
    String schemaVersion, String calculoVersao, String status,
    LocalDate dataReferencia, LocalDate inicioHistorico, Turno turno,
    ExecucaoPlanejamento execucaoPlanejamento, Atendimentos atendimentos,
    List<ItemRanking> topComidas, List<AceitacaoItem> aceitacaoItens,
    List<AceitacaoItem> aceitacaoItensSemana,
    long quantidadeFechamentosDoMes, long quantidadeFechamentosDaSemana,
    List<LocalDate> datasFechamentosDoMes,
    List<TurmaIndicadores> porTurma,
    Aceitacao aceitacao, Desperdicio desperdicio,
    AnaliseIngredientes ingredientes, AnaliseIndisponivel rotacaoCardapio, List<String> avisos
) {
    public record ExecucaoPlanejamento(long refeicoesPlanejadas, long consumosRegistrados,
        BigDecimal percentual, BigDecimal metaPercentual, BigDecimal diferencaMetaPp, String statusMeta) {}
    public record Atendimentos(long alunosUnicos, long repeticoes) {}
    public record ItemRanking(String item, long planejamentos, long execucoesRegistradas,
        int escolas, List<String> origens, String metrica, BigDecimal percentual) {}
    public record AceitacaoItem(UUID receitaId, String item, long porcoesPreparadas, long porcoesServidas,
        long porcoesConsumidas, long restoNoPrato, BigDecimal percentual, long fechamentos) {}
    public record TurmaIndicadores(String turma, long consumosRegistrados, long alunosUnicos,
        long repeticoes, BigDecimal percentual, String statusMeta, String motivo) {}
    public record AnaliseIndisponivel(String status, String motivo, Integer cicloSugeridoDias) {}

    /** Medida pelo que voltou no prato. Nao confundir com execucao do planejamento. */
    public record Aceitacao(String status, String nivel, BigDecimal percentual,
        Long porcoesServidas, Long restoNoPrato, String motivo) {}

    /** Perda sobre o preparado: sobra na cuba mais resto no prato. */
    public record Desperdicio(String status, String risco, BigDecimal percentual,
        Long porcoesPreparadas, Long sobraNaoDistribuida, Long restoNoPrato, String motivo) {}

    public record IngredienteResto(String ingredienteId, String ingrediente, long amostra,
        BigDecimal percentualRestoCom, BigDecimal percentualRestoSem, BigDecimal diferencaPp) {}

    public record AnaliseIngredientes(String status, BigDecimal basePercentualResto,
        List<IngredienteResto> acimaDaBase, String motivo) {}

    public static IndicadoresLogisticos indisponivel(LocalDate data, Turno turno) {
        return new IndicadoresLogisticos("3", "indicadores-v1", "INDISPONIVEL", data,
            data.minusDays(29), turno, null, null, List.of(), List.of(), List.of(), 0, 0, List.of(), List.of(), null, null, null, null,
            List.of("Serviço de indicadores indisponível. Nenhum percentual foi estimado pela IA."));
    }
}
