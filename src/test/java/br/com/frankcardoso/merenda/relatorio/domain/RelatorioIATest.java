package br.com.frankcardoso.merenda.relatorio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RelatorioIATest {

    private static final Instant CRIADO_EM = Instant.parse("2026-08-26T12:00:00Z");

    @Test
    void deveConcluirRelatorioDepoisDoProcessamento() {
        var relatorio = RelatorioIA.pendente(LocalDate.of(2026, 8, 26), Turno.MANHA, CRIADO_EM);

        relatorio.iniciar("gemini", "gemini-2.5-flash", "{\"consumosAutorizados\":80}",
            CRIADO_EM.plusSeconds(1));
        relatorio.concluir("{\"riscoDesperdicio\":\"BAIXO\"}", "Baixo risco",
            CRIADO_EM.plusSeconds(2));

        assertThat(relatorio.getStatus()).isEqualTo(StatusRelatorioIA.CONCLUIDO);
        assertThat(relatorio.getTentativas()).isEqualTo(1);
        assertThat(relatorio.getProvedor()).isEqualTo("gemini");
        assertThat(relatorio.getResumo()).isEqualTo("Baixo risco");
        assertThat(relatorio.getConcluidoEm()).isEqualTo(CRIADO_EM.plusSeconds(2));
    }

    @Test
    void naoDeveConcluirRelatorioQueAindaEstaPendente() {
        var relatorio = RelatorioIA.pendente(LocalDate.of(2026, 8, 26), Turno.TARDE, CRIADO_EM);

        assertThatThrownBy(() -> relatorio.concluir("{}", "Resumo", CRIADO_EM.plusSeconds(1)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Relatorio nao esta em processamento");
    }

    @Test
    void devePermitirNovaTentativaDepoisDeFalha() {
        var relatorio = RelatorioIA.pendente(LocalDate.of(2026, 8, 26), Turno.NOITE, CRIADO_EM);
        relatorio.iniciar("gemini", "gemini-2.5-flash", "{}", CRIADO_EM.plusSeconds(1));
        relatorio.falhar("Falha temporaria", CRIADO_EM.plusSeconds(2));

        relatorio.iniciar("gemini", "gemini-2.5-flash", "{}", CRIADO_EM.plusSeconds(3));

        assertThat(relatorio.getStatus()).isEqualTo(StatusRelatorioIA.PROCESSANDO);
        assertThat(relatorio.getTentativas()).isEqualTo(2);
        assertThat(relatorio.getErro()).isNull();
        assertThat(relatorio.getConcluidoEm()).isNull();
    }
}
