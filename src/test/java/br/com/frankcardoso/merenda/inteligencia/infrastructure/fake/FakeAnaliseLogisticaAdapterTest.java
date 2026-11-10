package br.com.frankcardoso.merenda.inteligencia.infrastructure.fake;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.AcaoPlano;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.Aceitacao;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaInput;
import br.com.frankcardoso.merenda.inteligencia.application.port.ConclusaoDeterministica;
import br.com.frankcardoso.merenda.inteligencia.application.port.IndicadoresParaLLM;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FakeAnaliseLogisticaAdapterTest {

    private final FakeAnaliseLogisticaAdapter adapter = new FakeAnaliseLogisticaAdapter();

    private AnaliseLogisticaInput comIndicadores(Aceitacao aceitacao, List<AcaoPlano> plano) {
        var indicadores = new IndicadoresParaLLM("6", "indicadores-v1", "DISPONIVEL",
            LocalDate.of(2026, 8, 26), LocalDate.of(2026, 7, 28), null, null, null,
            aceitacao, null, null, null, List.of(), plano);
        return new AnaliseLogisticaInput(LocalDate.of(2026, 8, 26), "Lanche da tarde", "Sopa de legumes",
            List.of(), List.of(), List.of(), List.of(), indicadores, ConclusaoDeterministica.de(null, null));
    }

    @Test
    void resumoCurtoCitaAceitacaoEAPrimeiraAcao() {
        var plano = List.of(
            new AcaoPlano("REVISAR_PREPARACAO", null, "Sopa de legumes",
                "Revisar a preparação de Sopa de legumes: aceitação de 82% (meta 90%) em 6 dias medidos.",
                new BigDecimal("82"), new BigDecimal("90")),
            new AcaoPlano("REDUZIR_PREPARO", null, "Iogurte", "Preparar menos Iogurte.",
                new BigDecimal("18"), new BigDecimal("10")));

        var resultado = adapter.analisar(comIndicadores(
            new Aceitacao("DISPONIVEL", "MEDIA", new BigDecimal("88.50"), 100L, 11L, "medido"), plano));

        assertThat(resultado.resumoExecutivo())
            .contains("88,5%")
            .contains("nos últimos 30 dias")
            .doesNotContain("na janela")
            .doesNotContain("::")
            .containsOnlyOnce(":")
            .contains("Revisar a preparação de Sopa de legumes")
            .doesNotContain("Preparar menos Iogurte");
        assertThat(resultado.evidencias()).isEmpty();
        assertThat(resultado.recomendacoes()).isEmpty();
        resultado.validar();
    }

    @Test
    void semMedicaoNemAcaoDizIssoEmVezDeInventarNumero() {
        var resultado = adapter.analisar(comIndicadores(
            new Aceitacao("DADOS_INSUFICIENTES", "NAO_AVALIAVEL", null, null, null, "sem medição"),
            List.of()));

        assertThat(resultado.nivelAceitacao()).isEqualTo("NAO_AVALIAVEL");
        assertThat(resultado.riscoDesperdicio()).isEqualTo("NAO_AVALIAVEL");
        assertThat(resultado.resumoExecutivo()).contains("Ainda não há medição suficiente")
            .contains("nos últimos 30 dias")
            .contains("Ainda não há ações a recomendar")
            .doesNotContain("Nenhuma ação necessária");
    }

    @Test
    void medidoSemAcaoDizQueTudoEstaDentroDasMetas() {
        var resultado = adapter.analisar(comIndicadores(
            new Aceitacao("DISPONIVEL", "ALTA", new BigDecimal("93"), 100L, 7L, "medido"), List.of()));

        assertThat(resultado.resumoExecutivo()).contains("93%").contains("Nenhuma ação necessária");
    }

    @Test
    void semIndicadoresNaoGarantePlanoVazio() {
        assertThat(ConclusaoDeterministica.resumoCurto(null))
            .contains("Ainda não há ações a recomendar").doesNotContain("Nenhuma ação necessária");
    }

    @Test
    void semIndicadoresAindaGeraResumoValido() {
        var resultado = adapter.analisar(new AnaliseLogisticaInput(
            LocalDate.of(2026, 8, 26), "Lanche da tarde", "Sopa de legumes"));

        resultado.validar();
        assertThat(resultado.resumoExecutivo()).isNotBlank();
    }
}
