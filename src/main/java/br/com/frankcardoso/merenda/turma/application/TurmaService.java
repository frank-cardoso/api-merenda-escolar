package br.com.frankcardoso.merenda.turma.application;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.shared.web.ConflitoCadastroException;
import br.com.frankcardoso.merenda.shared.web.RecursoNaoEncontradoException;
import br.com.frankcardoso.merenda.turma.domain.Turma;
import br.com.frankcardoso.merenda.turma.infrastructure.EnturmacaoRepository;
import br.com.frankcardoso.merenda.turma.infrastructure.TurmaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TurmaService {

    private final TurmaRepository turmas;
    private final EnturmacaoRepository enturmacoes;
    private final AuditoriaConsumoRepository auditoria;

    public TurmaService(TurmaRepository turmas, EnturmacaoRepository enturmacoes,
                        AuditoriaConsumoRepository auditoria) {
        this.turmas = turmas;
        this.enturmacoes = enturmacoes;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<Turma> listar() {
        return turmas.findAll(Sort.by("codigo"));
    }

    @Transactional(readOnly = true)
    public Turma buscar(UUID id) {
        return turmas.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Turma", id));
    }

    @Transactional
    public Turma criar(String codigo, String descricao, Turno turno, Integer quantidadeMaximaAlunos) {
        if (turmas.findByCodigo(codigo).isPresent()) {
            throw new ConflitoCadastroException("Ja existe turma com o codigo " + codigo);
        }
        return turmas.save(new Turma(UUID.randomUUID(), codigo, descricao, turno, quantidadeMaximaAlunos, null,
            Instant.now()));
    }

    @Transactional
    public Turma atualizar(UUID id, String codigo, String descricao, Turno turno, Integer quantidadeMaximaAlunos) {
        Turma turma = buscar(id);
        turmas.findByCodigo(codigo).filter(outra -> !outra.getId().equals(id)).ifPresent(outra -> {
            throw new ConflitoCadastroException("Ja existe turma com o codigo " + codigo);
        });
        turma.atualizar(codigo, descricao, turno, quantidadeMaximaAlunos);
        return turma;
    }

    /** Turma com vinculo ou com consumo carimbado e historico: nao se apaga. */
    @Transactional
    public void excluir(UUID id) {
        Turma turma = buscar(id);
        if (enturmacoes.existsByTurmaId(id)) {
            throw new ConflitoCadastroException("Turma possui alunos enturmados (atuais ou historicos)");
        }
        if (auditoria.existsByTurmaId(id)) {
            throw new ConflitoCadastroException("Turma possui consumos registrados");
        }
        turmas.delete(turma);
    }
}
