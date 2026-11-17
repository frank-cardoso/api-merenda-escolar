package br.com.frankcardoso.merenda.turma.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.shared.web.ConflitoCadastroException;
import br.com.frankcardoso.merenda.turma.domain.Turma;
import br.com.frankcardoso.merenda.turma.infrastructure.EnturmacaoRepository;
import br.com.frankcardoso.merenda.turma.infrastructure.TurmaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prova, contra o banco real, que o servico nunca deixa duas enturmacoes sobrepostas e que a
 * consulta usada a cada bipagem da fila (vigenteEm, que devolve Optional) continua valida.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:vigencia;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Transactional
class VigenciaFilaIntegracaoTest {

    @Autowired
    private EnturmacaoService service;
    @Autowired
    private EnturmacaoRepository enturmacoes;
    @Autowired
    private AlunoRepository alunos;
    @Autowired
    private TurmaRepository turmas;

    @Test
    void sobreposicaoERejeitadaEFilaNaoQuebraEmNenhumaData() {
        var aluno = alunos.saveAndFlush(new Aluno(UUID.randomUUID(), "ALU-VIG", "MAT-VIG", "Aluno Vigencia", true,
            null, Instant.now()));
        var a = turmas.saveAndFlush(new Turma(UUID.randomUUID(), "VIG-A", "A", Turno.MANHA, null, null, Instant.now()));
        var b = turmas.saveAndFlush(new Turma(UUID.randomUUID(), "VIG-B", "B", Turno.MANHA, null, null, Instant.now()));

        service.enturmar(aluno.getId(), a.getId(), LocalDate.of(2026, 2, 1));
        enturmacoes.flush();

        assertThatThrownBy(() -> service.enturmar(aluno.getId(), b.getId(), LocalDate.of(2026, 5, 1)))
            .isInstanceOf(ConflitoCadastroException.class);
        assertThatThrownBy(() -> service.enturmar(aluno.getId(), b.getId(), LocalDate.of(2026, 1, 1)))
            .isInstanceOf(ConflitoCadastroException.class);

        service.transferir(aluno.getId(), b.getId(), LocalDate.of(2026, 6, 10));
        enturmacoes.flush();
        assertThatThrownBy(() -> service.transferir(aluno.getId(), a.getId(), LocalDate.of(2026, 6, 10)))
            .isInstanceOf(IllegalArgumentException.class);
        service.transferir(aluno.getId(), a.getId(), LocalDate.of(2026, 9, 1));
        enturmacoes.flush();

        for (LocalDate dia = LocalDate.of(2026, 1, 1); dia.isBefore(LocalDate.of(2026, 12, 31)); dia = dia.plusDays(1)) {
            var vigente = enturmacoes.vigenteEm(aluno.getId(), dia);
            assertThat(vigente.isPresent()).isEqualTo(!dia.isBefore(LocalDate.of(2026, 2, 1)));
        }
        assertThat(enturmacoes.vigenteEm(aluno.getId(), LocalDate.of(2026, 6, 9)).orElseThrow().getTurma().getCodigo())
            .isEqualTo("VIG-A");
        assertThat(enturmacoes.vigenteEm(aluno.getId(), LocalDate.of(2026, 6, 10)).orElseThrow().getTurma().getCodigo())
            .isEqualTo("VIG-B");
        assertThat(enturmacoes.vigenteEm(aluno.getId(), LocalDate.of(2026, 9, 1)).orElseThrow().getTurma().getCodigo())
            .isEqualTo("VIG-A");
        assertThat(enturmacoes.findByAlunoIdOrderByDataInicioDesc(aluno.getId())).hasSize(3);
    }

    @Test
    void vigenciasSobrepostasPorEscritaExternaNaoDerrubamAFila() {
        var aluno = alunos.saveAndFlush(new Aluno(UUID.randomUUID(), "ALU-OVL", "MAT-OVL", "Aluno Overlap", true,
            null, Instant.now()));
        var a = turmas.saveAndFlush(new Turma(UUID.randomUUID(), "OVL-A", "A", Turno.MANHA, null, null, Instant.now()));
        var b = turmas.saveAndFlush(new Turma(UUID.randomUUID(), "OVL-B", "B", Turno.MANHA, null, null, Instant.now()));
        enturmacoes.saveAndFlush(new br.com.frankcardoso.merenda.turma.domain.Enturmacao(UUID.randomUUID(), aluno, a,
            LocalDate.of(2026, 2, 1), null, null));
        enturmacoes.saveAndFlush(new br.com.frankcardoso.merenda.turma.domain.Enturmacao(UUID.randomUUID(), aluno, b,
            LocalDate.of(2026, 3, 1), null, null));

        assertThat(enturmacoes.vigenteEm(aluno.getId(), LocalDate.of(2026, 4, 1)).orElseThrow().getTurma().getCodigo())
            .isEqualTo("OVL-B");
        assertThat(enturmacoes.vigenteEm(aluno.getId(), LocalDate.of(2026, 2, 15)).orElseThrow().getTurma().getCodigo())
            .isEqualTo("OVL-A");
    }
}
