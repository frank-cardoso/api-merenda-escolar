package br.com.frankcardoso.merenda.turma.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.shared.web.ConflitoCadastroException;
import br.com.frankcardoso.merenda.turma.domain.Turma;
import br.com.frankcardoso.merenda.turma.infrastructure.EnturmacaoRepository;
import br.com.frankcardoso.merenda.turma.infrastructure.TurmaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TurmaServiceTest {

    @Mock
    private TurmaRepository turmas;
    @Mock
    private EnturmacaoRepository enturmacoes;
    @Mock
    private AuditoriaConsumoRepository auditoria;

    private TurmaService service;
    private Turma turma;

    @BeforeEach
    void preparar() {
        service = new TurmaService(turmas, enturmacoes, auditoria);
        turma = new Turma(UUID.randomUUID(), "5A", "5o ano A", Turno.MANHA, 30, null, Instant.now());
        when(turmas.findById(turma.getId())).thenReturn(Optional.of(turma));
    }

    @Test
    void excluirRejeitaTurmaComEnturmacao() {
        when(enturmacoes.existsByTurmaId(turma.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.excluir(turma.getId())).isInstanceOf(ConflitoCadastroException.class);
        verify(turmas, never()).delete(turma);
    }

    @Test
    void excluirRejeitaTurmaComConsumoCarimbado() {
        when(enturmacoes.existsByTurmaId(turma.getId())).thenReturn(false);
        when(auditoria.existsByTurmaId(turma.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.excluir(turma.getId())).isInstanceOf(ConflitoCadastroException.class);
        verify(turmas, never()).delete(turma);
    }

    @Test
    void excluirRemoveTurmaSemUso() {
        when(enturmacoes.existsByTurmaId(turma.getId())).thenReturn(false);
        when(auditoria.existsByTurmaId(turma.getId())).thenReturn(false);

        service.excluir(turma.getId());

        verify(turmas).delete(turma);
        assertThat(turma.getCodigo()).isEqualTo("5A");
    }
}
