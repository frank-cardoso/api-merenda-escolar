package br.com.frankcardoso.merenda.turma.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.shared.web.ConflitoCadastroException;
import br.com.frankcardoso.merenda.turma.domain.Enturmacao;
import br.com.frankcardoso.merenda.turma.domain.Turma;
import br.com.frankcardoso.merenda.turma.infrastructure.EnturmacaoRepository;
import br.com.frankcardoso.merenda.turma.infrastructure.TurmaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnturmacaoServiceTest {

    @Mock
    private EnturmacaoRepository enturmacoes;
    @Mock
    private AlunoRepository alunos;
    @Mock
    private TurmaRepository turmas;

    private EnturmacaoService service;
    private Aluno aluno;
    private Turma turmaA;
    private Turma turmaB;

    @BeforeEach
    void preparar() {
        service = new EnturmacaoService(enturmacoes, alunos, turmas);
        var agora = Instant.parse("2026-01-01T00:00:00Z");
        aluno = new Aluno(UUID.randomUUID(), "ALU-1", "2026001", "Ana", true, null, agora);
        turmaA = new Turma(UUID.randomUUID(), "5A", "5o ano A", Turno.MANHA, 30, null, agora);
        turmaB = new Turma(UUID.randomUUID(), "5B", "5o ano B", Turno.MANHA, 30, null, agora);
        when(alunos.buscarParaAtualizar(aluno.getId())).thenReturn(Optional.of(aluno));
    }

    private void turmasExistem() {
        lenient().when(turmas.findById(turmaA.getId())).thenReturn(Optional.of(turmaA));
        lenient().when(turmas.findById(turmaB.getId())).thenReturn(Optional.of(turmaB));
    }

    private void vinculos(Enturmacao... existentes) {
        when(enturmacoes.findByAlunoIdOrderByDataInicioDesc(aluno.getId())).thenReturn(List.of(existentes));
    }

    private Enturmacao aberto(LocalDate inicio) {
        return new Enturmacao(UUID.randomUUID(), aluno, turmaA, inicio, null, null);
    }

    @Test
    void transferirFechaVinculoAnteriorNoDiaAnterior() {
        turmasExistem();
        var vigente = aberto(LocalDate.of(2026, 2, 1));
        vinculos(vigente);
        when(enturmacoes.save(any(Enturmacao.class))).thenAnswer(i -> i.getArgument(0));

        var novo = service.transferir(aluno.getId(), turmaB.getId(), LocalDate.of(2026, 6, 10));

        assertThat(vigente.getDataFim()).isEqualTo(LocalDate.of(2026, 6, 9));
        assertThat(novo).isNotNull();
        assertThat(novo.getTurma()).isSameAs(turmaB);
        assertThat(novo.getDataInicio()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(novo.getDataFim()).isNull();
        var salvos = ArgumentCaptor.forClass(Enturmacao.class);
        verify(enturmacoes, org.mockito.Mockito.atLeastOnce()).save(salvos.capture());
        assertThat(salvos.getAllValues()).contains(novo);
    }

    @Test
    void transferirRejeitaDataAnteriorAoInicioDoVinculoVigente() {
        turmasExistem();
        var vigente = aberto(LocalDate.of(2026, 2, 1));
        vinculos(vigente);

        assertThatThrownBy(() -> service.transferir(aluno.getId(), turmaB.getId(), LocalDate.of(2026, 1, 15)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.transferir(aluno.getId(), turmaB.getId(), LocalDate.of(2026, 2, 1)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(vigente.getDataFim()).isNull();
        verify(enturmacoes, never()).save(any());
    }

    @Test
    void transferirRejeitaMesmaTurmaESemVinculo() {
        turmasExistem();
        vinculos(aberto(LocalDate.of(2026, 2, 1)));
        assertThatThrownBy(() -> service.transferir(aluno.getId(), turmaA.getId(), LocalDate.of(2026, 6, 10)))
            .isInstanceOf(ConflitoCadastroException.class);

        vinculos();
        assertThatThrownBy(() -> service.transferir(aluno.getId(), turmaB.getId(), LocalDate.of(2026, 6, 10)))
            .isInstanceOf(ConflitoCadastroException.class);
        verify(enturmacoes, never()).save(any());
    }

    @Test
    void enturmarRejeitaSobreposicaoDeVigencia() {
        turmasExistem();
        vinculos(aberto(LocalDate.of(2026, 2, 1)));

        assertThatThrownBy(() -> service.enturmar(aluno.getId(), turmaB.getId(), LocalDate.of(2026, 8, 1)))
            .isInstanceOf(ConflitoCadastroException.class);
        verify(enturmacoes, never()).save(any());
    }

    @Test
    void enturmarRejeitaInicioDentroDeVinculoJaEncerrado() {
        turmasExistem();
        vinculos(new Enturmacao(UUID.randomUUID(), aluno, turmaA, LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 3, 31), null));

        assertThatThrownBy(() -> service.enturmar(aluno.getId(), turmaB.getId(), LocalDate.of(2026, 3, 31)))
            .isInstanceOf(ConflitoCadastroException.class);
        verify(enturmacoes, never()).save(any());
    }

    @Test
    void enturmarAceitaPrimeiroVinculoEPosteriorAoEncerrado() {
        turmasExistem();
        when(enturmacoes.save(any(Enturmacao.class))).thenAnswer(i -> i.getArgument(0));
        vinculos(new Enturmacao(UUID.randomUUID(), aluno, turmaA, LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 3, 31), null));

        var novo = service.enturmar(aluno.getId(), turmaB.getId(), LocalDate.of(2026, 4, 1));

        assertThat(novo).isNotNull();
        assertThat(novo.getTurma()).isSameAs(turmaB);
        assertThat(novo.getDataFim()).isNull();
    }
}
