package br.com.frankcardoso.merenda.analytics.application.port;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

public record IndicadoresLogisticosInput(
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate dataReferencia,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate inicioHistorico, Turno turno,
    int quantidadePlanejada, long consumosRegistrados, long alunosUnicos,
    BigDecimal metaPercentual, List<ContagemTurma> turmas, List<ContagemItem> itens
) {
    public record ContagemTurma(String turma, long consumosRegistrados, long alunosUnicos) {}
    public record ContagemItem(String item, long planejamentos, long execucoesRegistradas,
                               int escolas, List<String> origens) {}
}
