package br.com.frankcardoso.merenda.inteligencia.application.port;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.TurmaIndicadores;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class IndicadoresParaLLMTest {

    private static final String MOTIVO = "Sem registro de presença elegível.";

    private IndicadoresLogisticos comTurmas(List<TurmaIndicadores> turmas) {
        return new IndicadoresLogisticos("2", "indicadores-v1", "DISPONIVEL",
            LocalDate.of(2026, 9, 21), LocalDate.of(2026, 8, 23), Turno.MANHA,
            null, null, List.of(), turmas, null, null, null, null, List.of());
    }

    private TurmaIndicadores turma(String nome, long consumos, String statusMeta, BigDecimal pct) {
        return new TurmaIndicadores(nome, consumos, consumos, 0, pct, statusMeta, MOTIVO);
    }

    @Test
    void deveResumirTurmasQuandoNenhumaTemPercentualProprio() {
        var projecao = IndicadoresParaLLM.de(comTurmas(List.of(
            turma("1A", 9, "NAO_AVALIAVEL", null),
            turma("1B", 7, "NAO_AVALIAVEL", null),
            turma("2A", 5, "NAO_AVALIAVEL", null))));

        assertThat(projecao.porTurma()).isNull();
        assertThat(projecao.resumoTurmas().turmas()).isEqualTo(3);
        assertThat(projecao.resumoTurmas().statusMeta()).isEqualTo("NAO_AVALIAVEL");
        assertThat(projecao.resumoTurmas().consumosRegistrados()).isEqualTo(21);
        assertThat(projecao.resumoTurmas().alunosUnicos()).isEqualTo(21);
        // O motivo repetido em toda turma vira uma linha so.
        assertThat(projecao.resumoTurmas().motivos()).containsExactly(MOTIVO);
    }

    @Test
    void deveManterODetalhePorTurmaAssimQueAlgumaForAvaliavel() {
        var projecao = IndicadoresParaLLM.de(comTurmas(List.of(
            turma("1A", 9, "ABAIXO", new BigDecimal("42.00")),
            turma("1B", 7, "NAO_AVALIAVEL", null))));

        assertThat(projecao.resumoTurmas()).isNull();
        assertThat(projecao.porTurma()).hasSize(2);
    }

    @Test
    void deveManterListaVaziaQuandoNaoHaTurmas() {
        var projecao = IndicadoresParaLLM.de(comTurmas(List.of()));

        assertThat(projecao.resumoTurmas()).isNull();
        assertThat(projecao.porTurma()).isEmpty();
    }
}
