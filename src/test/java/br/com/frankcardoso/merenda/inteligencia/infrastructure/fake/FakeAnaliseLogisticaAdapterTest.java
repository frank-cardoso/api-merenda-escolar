package br.com.frankcardoso.merenda.inteligencia.infrastructure.fake;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FakeAnaliseLogisticaAdapterTest {

    private final FakeAnaliseLogisticaAdapter adapter = new FakeAnaliseLogisticaAdapter();

    private final AnaliseLogisticaInput input = new AnaliseLogisticaInput(
        LocalDate.of(2026, 8, 26), Turno.TARDE, "Sopa de legumes", 200, 80, 3,
        new BigDecimal("40.00"), 120);

    @Test
    void naoDeveInventarAceitacaoAPartirDeAutorizacoesDeConsumo() {
        var resultado = adapter.analisar(input);

        assertThat(resultado.nivelAceitacao()).isEqualTo("NAO_AVALIAVEL");
        assertThat(resultado.evidencias()).hasSize(2);
    }

    @Test
    void naoDeveClassificarDesperdicioAPartirDaSobraDePlanejamento() {
        var resultado = adapter.analisar(input);

        assertThat(resultado.riscoDesperdicio()).isEqualTo("NAO_AVALIAVEL");
        assertThat(resultado.evidencias()).anySatisfy(evidencia ->
            assertThat(evidencia).contains("sobra de planejamento, não desperdício medido"));
    }

    @Test
    void naoDeveRecomendarAjusteDeQuantidadePlanejada() {
        var resultado = adapter.analisar(input);

        assertThat(resultado.recomendacoes()).isNotEmpty();
        assertThat(resultado.recomendacoes()).noneMatch(recomendacao ->
            recomendacao.toLowerCase().matches(".*\\b(aumentar|reduzir|diminuir|ajustar)\\b.*"));
    }
}
