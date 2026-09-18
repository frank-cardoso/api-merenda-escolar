package br.com.frankcardoso.merenda.inteligencia.infrastructure.fake;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FakeAnaliseLogisticaAdapterTest {

    private final FakeAnaliseLogisticaAdapter adapter = new FakeAnaliseLogisticaAdapter();

    @Test
    void naoDeveInventarAceitacaoAPartirDeAutorizacoesDeConsumo() {
        var input = new AnaliseLogisticaInput(LocalDate.of(2026, 8, 26), Turno.TARDE,
            "Sopa de legumes", 200, 80, 3, new BigDecimal("40.00"), 120);

        var resultado = adapter.analisar(input);

        assertThat(resultado.nivelAceitacao()).isEqualTo("NAO_AVALIAVEL");
        assertThat(resultado.riscoDesperdicio()).isEqualTo("ALTO");
        assertThat(resultado.evidencias()).hasSize(2);
    }
}
