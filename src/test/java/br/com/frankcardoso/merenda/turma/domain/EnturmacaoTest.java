package br.com.frankcardoso.merenda.turma.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnturmacaoTest {

    private Enturmacao enturmacao(LocalDate inicio, LocalDate fim) {
        return new Enturmacao(UUID.randomUUID(), null, null, inicio, fim, null);
    }

    @Test
    void vigenteEmDentroDoIntervaloFechado() {
        var e = enturmacao(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 7, 31));
        assertThat(e.vigenteEm(LocalDate.of(2026, 7, 31))).isTrue();
        assertThat(e.vigenteEm(LocalDate.of(2026, 2, 1))).isTrue();
        assertThat(e.vigenteEm(LocalDate.of(2026, 8, 1))).isFalse();
    }

    @Test
    void vigenteEmComDataFimNula() {
        assertThat(enturmacao(LocalDate.of(2026, 8, 1), null).vigenteEm(LocalDate.of(2026, 12, 31))).isTrue();
    }

    @Test
    void naoVigenteAntesDoInicio() {
        assertThat(enturmacao(LocalDate.of(2026, 8, 1), null).vigenteEm(LocalDate.of(2026, 7, 31))).isFalse();
    }

    @Test
    void dataFimAnteriorAoInicioRejeitada() {
        assertThatThrownBy(() -> enturmacao(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 31)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
