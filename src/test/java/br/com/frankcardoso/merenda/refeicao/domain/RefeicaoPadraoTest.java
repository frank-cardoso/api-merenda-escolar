package br.com.frankcardoso.merenda.refeicao.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RefeicaoPadraoTest {

    @Test
    void turnoLegadoMapeiaParaARefeicao() {
        assertThat(RefeicaoPadrao.doTurnoLegado(Turno.MANHA)).isEqualTo(RefeicaoPadrao.LANCHE_DA_MANHA);
        assertThat(RefeicaoPadrao.doTurnoLegado(Turno.TARDE)).isEqualTo(RefeicaoPadrao.LANCHE_DA_TARDE);
        assertThat(RefeicaoPadrao.doTurnoLegado(Turno.NOITE)).isEqualTo(RefeicaoPadrao.LANCHE_DA_NOITE);
        assertThat(RefeicaoPadrao.doTurnoLegado(Turno.INTEGRAL)).isEqualTo(RefeicaoPadrao.ALMOCO);
    }

    @Test
    void nomesDoLegadoMapeiamComoAMigration() {
        assertThat(RefeicaoPadrao.doNomeLegado("Cafe de manha - Turnos Integrais"))
            .contains(RefeicaoPadrao.CAFE_DA_MANHA);
        assertThat(RefeicaoPadrao.doNomeLegado("Cafe da manha EMEIS ou menores de 1 ano"))
            .contains(RefeicaoPadrao.CAFE_DA_MANHA);
        assertThat(RefeicaoPadrao.doNomeLegado("Almoco - EMEIS")).contains(RefeicaoPadrao.ALMOCO);
        assertThat(RefeicaoPadrao.doNomeLegado("Lanche da manha - Jardim EMEIS"))
            .contains(RefeicaoPadrao.LANCHE_DA_MANHA);
        assertThat(RefeicaoPadrao.doNomeLegado("Lanche da tarde, menores de um ano - EMEIS"))
            .contains(RefeicaoPadrao.LANCHE_DA_TARDE);
        for (String nome : new String[] {"Lanches manha ou tarde - EMEFS parcial",
                "Lanche do turno estendido - EMEIS", "Frutas - Lanches",
                "Lanches manha e tarde - Jardim EMEIS"}) {
            assertThat(RefeicaoPadrao.doNomeLegado(nome)).isEqualTo(Optional.empty());
        }
    }
}
