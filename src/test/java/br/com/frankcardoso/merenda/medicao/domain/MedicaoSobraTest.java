package br.com.frankcardoso.merenda.medicao.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MedicaoSobraTest {

    private MedicaoSobra criar(int preparadas, int servidas, int sobra, int resto) {
        return new MedicaoSobra(UUID.randomUUID(), LocalDate.of(2026, 9, 21), Turno.MANHA, 1,
            UUID.randomUUID(), preparadas, servidas, sobra, resto, "LANCADA", Instant.EPOCH);
    }

    @Test
    void deveAceitarMedicaoComNumerosQueFecham() {
        assertThatCode(() -> criar(100, 91, 9, 11)).doesNotThrowAnyException();
    }

    @Test
    void deveRecusarPreparadasQueNaoSaoASomaDeServidasComSobra() {
        assertThatThrownBy(() -> criar(100, 91, 5, 0))
            .isInstanceOf(MedicaoSobraInconsistenteException.class)
            .hasMessageContaining("devem ser a soma");
    }

    @Test
    void deveRecusarRestoMaiorQueOServido() {
        assertThatThrownBy(() -> criar(100, 91, 9, 92))
            .isInstanceOf(MedicaoSobraInconsistenteException.class)
            .hasMessageContaining("não pode superar");
    }

    @Test
    void deveRecusarPorcoesNegativas() {
        assertThatThrownBy(() -> criar(100, 91, 9, -1))
            .isInstanceOf(MedicaoSobraInconsistenteException.class)
            .hasMessageContaining("negativas");
    }
}
