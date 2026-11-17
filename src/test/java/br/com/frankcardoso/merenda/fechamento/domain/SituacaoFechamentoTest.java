package br.com.frankcardoso.merenda.fechamento.domain;

import static br.com.frankcardoso.merenda.fechamento.domain.SituacaoFechamento.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class SituacaoFechamentoTest {

    private static final LocalTime INICIO = LocalTime.of(9, 30);
    private static final LocalTime FIM = LocalTime.of(10, 30);
    private static final LocalDate HOJE = LocalDate.of(2026, 3, 10);

    private SituacaoFechamento de(boolean cardapio, boolean completa, LocalDate data, LocalTime agora) {
        return SituacaoFechamento.de(cardapio, completa, data, INICIO, FIM, HOJE, agora);
    }

    @Test
    void semCardapioVenceMesmoComMedicaoCompleta() {
        assertThat(de(false, true, HOJE, LocalTime.of(10, 0))).isEqualTo(SEM_CARDAPIO);
    }

    @Test
    void completaEstaFechadaEmQualquerData() {
        assertThat(de(true, true, LocalDate.of(2026, 3, 9), LocalTime.of(10, 0))).isEqualTo(FECHADA);
        assertThat(de(true, true, HOJE, LocalTime.of(10, 0))).isEqualTo(FECHADA);
        assertThat(de(true, true, LocalDate.of(2026, 3, 11), LocalTime.of(10, 0))).isEqualTo(FECHADA);
    }

    @Test
    void hojeSegueAJanelaSemiAberta() {
        assertThat(de(true, false, HOJE, LocalTime.of(9, 29))).isEqualTo(AGUARDANDO);
        assertThat(de(true, false, HOJE, LocalTime.of(9, 30))).isEqualTo(EM_ANDAMENTO);
        assertThat(de(true, false, HOJE, LocalTime.of(10, 29))).isEqualTo(EM_ANDAMENTO);
        assertThat(de(true, false, HOJE, LocalTime.of(10, 30))).isEqualTo(PENDENTE);
    }

    @Test
    void dataPassadaPendenteEFuturaAguardando() {
        assertThat(de(true, false, LocalDate.of(2026, 3, 9), LocalTime.of(9, 0))).isEqualTo(PENDENTE);
        assertThat(de(true, false, LocalDate.of(2026, 3, 11), LocalTime.of(23, 0))).isEqualTo(AGUARDANDO);
    }
}
