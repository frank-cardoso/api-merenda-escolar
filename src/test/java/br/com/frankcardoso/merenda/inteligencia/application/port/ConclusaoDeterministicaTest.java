package br.com.frankcardoso.merenda.inteligencia.application.port;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.Aceitacao;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.AnaliseIngredientes;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.Desperdicio;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.IngredienteResto;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConclusaoDeterministicaTest {

    private IndicadoresLogisticos indicadores(
        Aceitacao aceitacao, Desperdicio desperdicio, AnaliseIngredientes ingredientes
    ) {
        return new IndicadoresLogisticos("3", "indicadores-v1", "DISPONIVEL",
            LocalDate.of(2026, 9, 21), LocalDate.of(2026, 8, 23), Turno.MANHA,
            new IndicadoresLogisticos.ExecucaoPlanejamento(300, 91, new BigDecimal("30.33"),
                new BigDecimal("80.00"), new BigDecimal("-49.67"), "ABAIXO"),
            new IndicadoresLogisticos.Atendimentos(91, 0),
            List.of(), List.of(), aceitacao, desperdicio, ingredientes, null, List.of());
    }

    @Test
    void semMedicaoAsDuasClassificacoesFicamNaoAvaliaveisEViramLimitacao() {
        var conclusao = ConclusaoDeterministica.de(indicadores(
            new Aceitacao("DADOS_INSUFICIENTES", "NAO_AVALIAVEL", null, null, null, "sem medição"),
            new Desperdicio("DADOS_INSUFICIENTES", "NAO_AVALIAVEL", null, null, null, null, "sem medição"),
            new AnaliseIngredientes("DADOS_INSUFICIENTES", null, List.of(), "sem base")), null);

        assertThat(conclusao.nivelAceitacao()).isEqualTo("NAO_AVALIAVEL");
        assertThat(conclusao.riscoDesperdicio()).isEqualTo("NAO_AVALIAVEL");
        assertThat(conclusao.limitacoes()).anyMatch(l -> l.startsWith("Aceitacao alimentar nao e avaliavel"));
        assertThat(conclusao.afirmacoes()).noneMatch(a -> a.contains("aceitacao medida"));
    }

    @Test
    void comMedicaoAsClassificacoesVemDoCalculoEAsRessalvasSaem() {
        var conclusao = ConclusaoDeterministica.de(indicadores(
            new Aceitacao("DISPONIVEL", "ALTA", new BigDecimal("93.68"), 364L, 23L, "medido"),
            new Desperdicio("DISPONIVEL", "MEDIO", new BigDecimal("12.56"), 390L, 26L, 23L, "medido"),
            new AnaliseIngredientes("DISPONIVEL", new BigDecimal("9.43"),
                List.of(new IngredienteResto("i-chuchu", "chuchu", 78, new BigDecimal("29.89"),
                    new BigDecimal("9.43"), new BigDecimal("20.46"))), "associação")), null);

        assertThat(conclusao.nivelAceitacao()).isEqualTo("ALTA");
        assertThat(conclusao.riscoDesperdicio()).isEqualTo("MEDIO");
        assertThat(conclusao.afirmacoes()).contains(
            "A aceitacao medida foi de 93,68% (364 porcoes servidas, 23 de resto no prato), nivel ALTA.",
            "Resto maior nos pratos que levam o ingrediente do que nos que nao levam "
                + "(media da janela 9,43%): chuchu 29,89% com contra 9,43% sem (20,46 pp, n=78).");
        // Sem ressalva de "nao e avaliavel" quando existe medicao.
        assertThat(conclusao.limitacoes()).noneMatch(l -> l.startsWith("Aceitacao alimentar nao e avaliavel"));
        // Mas a de associacao entra justamente porque ha ingrediente destacado.
        assertThat(conclusao.limitacoes()).anyMatch(l -> l.startsWith("Ingrediente acima dos pratos sem ele"));
    }
}
