package br.com.frankcardoso.merenda.inteligencia.application.port;

import static br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao.LANCHE_DA_MANHA;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.TurmaIndicadores;
import br.com.frankcardoso.merenda.analytics.application.port.RefeicaoRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IndicadoresParaLLMTest {

    private static final String MOTIVO = "Sem registro de presença elegível.";
    private static final RefeicaoRef REFEICAO = new RefeicaoRef(LANCHE_DA_MANHA.id(), LANCHE_DA_MANHA.nome());

    private IndicadoresLogisticos comTurmas(List<TurmaIndicadores> turmas) {
        return new IndicadoresLogisticos("6", "indicadores-v1", "DISPONIVEL",
            LocalDate.of(2026, 9, 21), LocalDate.of(2026, 8, 23), null, REFEICAO,
            null, null, List.of(), List.of(), List.of(), 0, 0, List.of(), turmas, null, null, null, null, List.of(), List.of());
    }

    private TurmaIndicadores turma(String nome, long consumos, String statusMeta, BigDecimal pct) {
        return new TurmaIndicadores(null, nome, consumos, consumos, 0, pct, statusMeta, MOTIVO);
    }

    @Test
    void filtroDeTurmaAvisaQueAceitacaoContinuaDaRefeicaoInteira() {
        var turmaId = UUID.randomUUID();
        var projecao = IndicadoresParaLLM.de(comTurmas(List.of(
            new TurmaIndicadores(turmaId, "1A", 9, 8, 1, null, "NAO_AVALIAVEL", MOTIVO),
            turma("1B", 7, "NAO_AVALIAVEL", null))), turmaId);

        assertThat(projecao.refeicao()).isEqualTo(REFEICAO);
        assertThat(projecao.avisos()).singleElement().satisfies(aviso ->
            assertThat(aviso).contains("refeição inteira"));
    }

    @Test
    void levaPlanoDeAcaoEMediasDaJanelaSemOsNumerosDoDia() {
        var acao = new IndicadoresLogisticos.AcaoPlano("REVISAR_PREPARACAO", null, "Sopa de legumes",
            "Revisar a preparação de Sopa de legumes.", new BigDecimal("82"), new BigDecimal("90"));
        var origem = comExecucao(List.of(acao));

        var projecao = IndicadoresParaLLM.de(origem);

        assertThat(projecao.planoDeAcao()).containsExactly(acao);
        assertThat(projecao.execucaoPlanejamento().mediaAtendidosDia()).isEqualByComparingTo("44");
        assertThat(projecao.execucaoPlanejamento().mediaPlanejadaDia()).isEqualByComparingTo("51");
        assertThat(projecao.execucaoPlanejamento().percentual()).isEqualByComparingTo("86.3");
    }

    @Test
    void percentualDaJanelaPrefereOValorDeReverPlanejamento() {
        var acao = new IndicadoresLogisticos.AcaoPlano("REVER_PLANEJAMENTO", null, null,
            "Rever o planejamento.", new BigDecimal("87.1"), new BigDecimal("90"));

        var projecao = IndicadoresParaLLM.de(comExecucao(List.of(acao)));

        assertThat(projecao.execucaoPlanejamento().percentual()).isEqualByComparingTo("87.1");
    }

    @Test
    void naoEnviaContagensDoDiaDeReferenciaAoModelo() throws Exception {
        var projecao = IndicadoresParaLLM.de(comExecucao(List.of()));

        var json = new com.fasterxml.jackson.databind.ObjectMapper()
            .findAndRegisterModules().valueToTree(projecao);

        var execucao = json.path("execucaoPlanejamento");
        assertThat(execucao.has("refeicoesPlanejadas")).isFalse();
        assertThat(execucao.has("consumosRegistrados")).isFalse();
        assertThat(execucao.has("metaPercentual")).isFalse();
        assertThat(execucao.has("mediaAtendidosDia")).isTrue();
        assertThat(json.has("atendimentos")).isFalse();
        assertThat(json.has("porTurma")).isFalse();
        assertThat(json.has("resumoTurmas")).isFalse();
        assertThat(json.toString()).doesNotContain("33.33");
    }

    private IndicadoresLogisticos comExecucao(List<IndicadoresLogisticos.AcaoPlano> plano) {
        var execucao = new IndicadoresLogisticos.ExecucaoPlanejamento(51, 17, new BigDecimal("33.33"),
            new BigDecimal("80"), new BigDecimal("-46.67"), "ABAIXO", new BigDecimal("44"),
            new BigDecimal("51"));
        return new IndicadoresLogisticos("6", "indicadores-v1", "DISPONIVEL",
            LocalDate.of(2026, 9, 21), LocalDate.of(2026, 8, 23), null, REFEICAO,
            execucao, new IndicadoresLogisticos.Atendimentos(17, 0), List.of(), List.of(), List.of(), 0, 0,
            List.of(), List.of(turma("1A", 9, "NAO_AVALIAVEL", null)),
            null, null, null, null, List.of(), plano);
    }
}
