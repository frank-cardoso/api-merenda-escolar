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
        return new IndicadoresLogisticos("6", "indicadores-v1", "DISPONIVEL",
            LocalDate.of(2026, 9, 21), LocalDate.of(2026, 8, 23), Turno.MANHA, null,
            new IndicadoresLogisticos.ExecucaoPlanejamento(300, 91, new BigDecimal("30.33"),
                new BigDecimal("80.00"), new BigDecimal("-49.67"), "ABAIXO", null, null),
            new IndicadoresLogisticos.Atendimentos(91, 0),
            List.of(), List.of(), List.of(), 0, 0, List.of(), List.of(), aceitacao, desperdicio, ingredientes, null, List.of(), List.of());
    }

    @Test
    void semMedicaoAsDuasClassificacoesFicamNaoAvaliaveisEViramLimitacao() {
        var conclusao = ConclusaoDeterministica.de(indicadores(
            new Aceitacao("DADOS_INSUFICIENTES", "NAO_AVALIAVEL", null, null, null, "sem medição"),
            new Desperdicio("DADOS_INSUFICIENTES", "NAO_AVALIAVEL", null, null, null, null, "sem medição"),
            new AnaliseIngredientes("DADOS_INSUFICIENTES", null, List.of(), "sem base")), null);

        assertThat(conclusao.nivelAceitacao()).isEqualTo("NAO_AVALIAVEL");
        assertThat(conclusao.riscoDesperdicio()).isEqualTo("NAO_AVALIAVEL");
        assertThat(conclusao.limitacoes()).anyMatch(l -> l.startsWith("A aceitação alimentar não é avaliável"));
        assertThat(conclusao.afirmacoes()).noneMatch(a -> a.contains("aceitação medida"));
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
            "A aceitação medida no período foi de 93,7% (364 porções servidas, 23 de resto no prato), nível alta.",
            "Resto maior nos pratos que levam o ingrediente do que nos que não levam "
                + "(média da janela 9,4%): chuchu 29,9% com contra 9,4% sem (20,5 pp, n=78).");
        // Sem ressalva de "nao e avaliavel" quando existe medicao.
        assertThat(conclusao.limitacoes()).noneMatch(l -> l.startsWith("A aceitação alimentar não é avaliável"));
        // Mas a de associacao entra justamente porque ha ingrediente destacado.
        assertThat(conclusao.limitacoes()).anyMatch(l -> l.startsWith("Ingrediente com mais resto que os pratos sem ele"));
    }

    @Test
    void textosFicamComAcentoEPercentualSemZeroSobrando() {
        var conclusao = ConclusaoDeterministica.de(indicadores(
            new Aceitacao("DISPONIVEL", "MEDIA", new BigDecimal("82.00"), 100L, 18L, "medido"),
            new Desperdicio("DISPONIVEL", "BAIXO", new BigDecimal("8.50"), 120L, 4L, 18L, "medido"),
            new AnaliseIngredientes("DADOS_INSUFICIENTES", null, List.of(), "sem base")), null);

        assertThat(conclusao.afirmacoes()).anyMatch(a -> a.contains("82% (100 porções"));
        assertThat(conclusao.afirmacoes()).anyMatch(a -> a.contains("8,5% do preparado"));
        assertThat(conclusao.limitacoes()).noneMatch(l -> l.contains("aceitação alimentar não é avaliável"));
        assertThat(String.join(" ", conclusao.afirmacoes()) + String.join(" ", conclusao.limitacoes()))
            .doesNotContain("execucao", "periodo", "porcoes", "nao ", "cuba");
    }

    @Test
    void execucaoUsaAsMediasDosUltimos30DiasENuncaOsNumerosDoDia() {
        var execucao = new IndicadoresLogisticos.ExecucaoPlanejamento(51, 17, new BigDecimal("33.33"),
            new BigDecimal("80.00"), new BigDecimal("-46.67"), "ABAIXO",
            new BigDecimal("44.4"), new BigDecimal("51"));
        var origem = new IndicadoresLogisticos("6", "indicadores-v1", "DISPONIVEL",
            LocalDate.of(2026, 9, 21), LocalDate.of(2026, 8, 23), Turno.MANHA, null, execucao,
            new IndicadoresLogisticos.Atendimentos(17, 0), List.of(), List.of(), List.of(), 0, 0,
            List.of(), List.of(), null, null, null, null, List.of(), List.of());

        var afirmacoes = ConclusaoDeterministica.de(origem, null).afirmacoes();

        assertThat(afirmacoes).contains("Nos últimos 30 dias, foram servidas em média 44,4 refeições "
            + "por dia para 51 planejadas (87,1%).");
        assertThat(String.join(" ", afirmacoes)).doesNotContain("33,3", "17 de 51", "meta de execução",
            "alunos únicos", "estimativa de demanda");
    }

    @Test
    void semMediasDaJanelaNaoHaAfirmacaoDeExecucao() {
        var afirmacoes = ConclusaoDeterministica.de(indicadores(null, null, null), null).afirmacoes();

        assertThat(String.join(" ", afirmacoes)).doesNotContain("30,3", "91 de 300");
    }

    @Test
    void niveisERiscosSaemPorExtensoComAcento() {
        var esperado = java.util.Map.of("ALTA", "alta", "MEDIA", "média", "BAIXA", "baixa",
            "NAO_AVALIAVEL", "não avaliável");
        esperado.forEach((nivel, texto) -> assertThat(ConclusaoDeterministica.de(indicadores(
            new Aceitacao("DISPONIVEL", nivel, new BigDecimal("80"), 100L, 20L, "medido"),
            null, null), null).afirmacoes()).anyMatch(a -> a.endsWith("nível " + texto + ".")));

        var riscos = java.util.Map.of("BAIXO", "baixo", "MEDIO", "médio", "ALTO", "alto",
            "NAO_AVALIAVEL", "não avaliável");
        riscos.forEach((risco, texto) -> assertThat(ConclusaoDeterministica.de(indicadores(
            null, new Desperdicio("DISPONIVEL", risco, new BigDecimal("12"), 100L, 5L, 7L, "medido"),
            null), null).afirmacoes()).anyMatch(a -> a.endsWith("risco " + texto + ".")));
    }

    @Test
    void nivelOuRiscoNuloNaoQuebraOTexto() {
        var conclusao = ConclusaoDeterministica.de(indicadores(
            new Aceitacao("DISPONIVEL", null, new BigDecimal("80"), 100L, 20L, "medido"),
            new Desperdicio("DISPONIVEL", null, new BigDecimal("12"), 100L, 5L, 7L, "medido"), null), null);

        assertThat(conclusao.afirmacoes()).anyMatch(a -> a.endsWith("nível não avaliável."));
        assertThat(conclusao.afirmacoes()).anyMatch(a -> a.endsWith("risco não avaliável."));
    }

    @Test
    void limitacoesFixasNaoTemTermoTecnicoNemFaltaDeAcento() {
        var texto = String.join(" ", ConclusaoDeterministica.de(indicadores(
            new Aceitacao("DADOS_INSUFICIENTES", "NAO_AVALIAVEL", null, null, null, "m"),
            new Desperdicio("DADOS_INSUFICIENTES", "NAO_AVALIAVEL", null, null, null, null, "m"),
            null), null).limitacoes());

        assertThat(texto).doesNotContain("ranking", "Ranking")
            .contains("A ordem dos itens mostra o período analisado; não prova queda ou melhora ao longo do tempo.");
    }
}
