package br.com.frankcardoso.merenda.refeicao.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GradeRefeicoesTest {

    private static final String FORA = "FORA_DO_HORARIO_DE_REFEICAO";
    private static final String SEM_DIREITO = "REFEICAO_NAO_PREVISTA_PARA_TURMA";

    private final GradeRefeicoes grade = GradeRefeicoes.de(List.of(
        refeicao("Café da manhã", 1, "07:00", "08:00", Turno.INTEGRAL),
        refeicao("Lanche da manhã", 2, "09:30", "10:30", Turno.MANHA, Turno.INTEGRAL),
        refeicao("Almoço", 3, "11:30", "13:00", Turno.INTEGRAL),
        refeicao("Lanche da tarde", 4, "15:00", "16:00", Turno.TARDE, Turno.INTEGRAL),
        refeicao("Lanche da noite", 5, "20:00", "21:00", Turno.NOITE)));

    @Test
    void cadaJanelaResolveSuaRefeicao() {
        assertThat(nome("07:00", Turno.INTEGRAL)).isEqualTo("Café da manhã");
        assertThat(nome("07:59", Turno.INTEGRAL)).isEqualTo("Café da manhã");
        assertThat(nome("09:30", Turno.MANHA)).isEqualTo("Lanche da manhã");
        assertThat(nome("10:00", Turno.INTEGRAL)).isEqualTo("Lanche da manhã");
        assertThat(nome("12:00", Turno.INTEGRAL)).isEqualTo("Almoço");
        assertThat(nome("15:30", Turno.TARDE)).isEqualTo("Lanche da tarde");
        assertThat(nome("20:30", Turno.NOITE)).isEqualTo("Lanche da noite");
    }

    @Test
    void limitesDaJanela() {
        assertThat(grade.resolver(LocalTime.parse("08:00"), Turno.INTEGRAL).motivoBloqueio()).isEqualTo(FORA);
        assertThat(grade.resolver(LocalTime.parse("13:00"), Turno.INTEGRAL).motivoBloqueio()).isEqualTo(FORA);
        assertThat(nome("11:30", Turno.INTEGRAL)).isEqualTo("Almoço");
    }

    @Test
    void foraDeQualquerJanela() {
        assertThat(grade.bloqueioDoHorario(LocalTime.parse("14:00"))).isEqualTo(Optional.of(FORA));
        assertThat(grade.bloqueioDoHorario(LocalTime.parse("12:00"))).isEmpty();
    }

    @Test
    void semDireitoNaJanela() {
        assertThat(grade.resolver(LocalTime.parse("12:00"), Turno.MANHA).motivoBloqueio()).isEqualTo(SEM_DIREITO);
        assertThat(grade.resolver(LocalTime.parse("07:30"), Turno.TARDE).motivoBloqueio()).isEqualTo(SEM_DIREITO);
    }

    @Test
    void bloqueioPorTurmaInformaARefeicaoDaJanela() {
        var resolucao = grade.resolver(LocalTime.parse("12:00"), Turno.MANHA);

        assertThat(resolucao.refeicao()).isNull();
        assertThat(resolucao.refeicaoNaJanela().getNome()).isEqualTo("Almoço");
        assertThat(grade.resolver(LocalTime.parse("12:00"), Turno.INTEGRAL).refeicaoNaJanela()).isNull();
        assertThat(grade.resolver(LocalTime.parse("14:00"), Turno.MANHA).refeicaoNaJanela()).isNull();
    }

    @Test
    void bloqueioPorTurmaComDuasRefeicoesNaJanelaInformaAMenorOrdem() {
        var sobreposta = GradeRefeicoes.de(List.of(
            refeicao("B", 2, "07:30", "08:30", Turno.TARDE),
            refeicao("A", 1, "07:00", "08:00", Turno.MANHA)));

        var resolucao = sobreposta.resolver(LocalTime.parse("07:45"), Turno.NOITE);

        assertThat(resolucao.motivoBloqueio()).isEqualTo(SEM_DIREITO);
        assertThat(resolucao.refeicaoNaJanela().getNome()).isEqualTo("A");
    }

    @Test
    void semTurmaValeAJanelaDoHorario() {
        assertThat(nome("12:00", null)).isEqualTo("Almoço");
    }

    @Test
    void semTurmaComJanelasSobrepostasDeTurnosDiferentesValeMenorOrdem() {
        var sobreposta = GradeRefeicoes.de(List.of(
            refeicao("A", 1, "07:00", "08:00", Turno.MANHA),
            refeicao("B", 2, "07:30", "08:30", Turno.TARDE)));

        assertThat(sobreposta.resolver(LocalTime.parse("07:45"), null).refeicao().getNome()).isEqualTo("A");
        assertThat(sobreposta.resolver(LocalTime.parse("07:45"), Turno.TARDE).refeicao().getNome()).isEqualTo("B");
    }

    @Test
    void sobreposicaoNoMesmoTurnoEhRecusada() {
        var a = refeicao("A", 1, "07:00", "08:00", Turno.INTEGRAL);
        var b = refeicao("B", 2, "07:30", "09:00", Turno.INTEGRAL);

        assertThatThrownBy(() -> GradeRefeicoes.de(List.of(a, b)))
            .isInstanceOf(GradeRefeicoesInvalidaException.class);
    }

    @Test
    void refeicaoSemTurnosNaoDisputaAJanelaComAAtiva() {
        var g = GradeRefeicoes.de(List.of(
            refeicao("Aposentada", 1, "07:00", "08:00"),
            refeicao("Ativa", 2, "07:30", "08:30", Turno.MANHA)));

        assertThat(g.resolver(LocalTime.parse("07:45"), null).refeicao().getNome()).isEqualTo("Ativa");
        assertThat(g.resolver(LocalTime.parse("07:45"), Turno.MANHA).refeicao().getNome()).isEqualTo("Ativa");
    }

    @Test
    void refeicaoSemTurnosSozinhaNaJanelaEstaForaDoHorario() {
        var g = GradeRefeicoes.de(List.of(
            refeicao("Aposentada", 1, "07:00", "08:00"),
            refeicao("Ativa", 2, "12:00", "13:00", Turno.MANHA)));

        assertThat(g.resolver(LocalTime.parse("07:30"), null).motivoBloqueio()).isEqualTo(FORA);
        assertThat(g.resolver(LocalTime.parse("07:30"), Turno.MANHA).motivoBloqueio()).isEqualTo(FORA);
        assertThat(g.bloqueioDoHorario(LocalTime.parse("07:30"))).isEqualTo(Optional.of(FORA));
    }

    @Test
    void bloqueioPorTurmaNomeiaARefeicaoAtivaENaoAAposentada() {
        var g = GradeRefeicoes.de(List.of(
            refeicao("Aposentada", 1, "07:00", "08:00"),
            refeicao("Ativa", 2, "07:30", "08:30", Turno.TARDE)));

        var resolucao = g.resolver(LocalTime.parse("07:45"), Turno.MANHA);

        assertThat(resolucao.motivoBloqueio()).isEqualTo(SEM_DIREITO);
        assertThat(resolucao.refeicaoNaJanela().getNome()).isEqualTo("Ativa");
    }

    @Test
    void gradeVaziaEhInvalida() {
        assertThatThrownBy(() -> GradeRefeicoes.de(List.of()))
            .isInstanceOf(GradeRefeicoesInvalidaException.class);
    }

    private String nome(String horario, Turno turno) {
        return grade.resolver(LocalTime.parse(horario), turno).refeicao().getNome();
    }

    private static TipoRefeicao refeicao(String nome, int ordem, String inicio, String fim, Turno... turnos) {
        return new TipoRefeicao(UUID.randomUUID(), nome, ordem, LocalTime.parse(inicio), LocalTime.parse(fim),
            Set.of(turnos));
    }
}
