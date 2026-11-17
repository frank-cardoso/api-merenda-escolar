package br.com.frankcardoso.merenda.turma.application;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.shared.web.ConflitoCadastroException;
import br.com.frankcardoso.merenda.shared.web.DadosInvalidosException;
import br.com.frankcardoso.merenda.shared.web.RecursoNaoEncontradoException;
import br.com.frankcardoso.merenda.turma.domain.Enturmacao;
import br.com.frankcardoso.merenda.turma.domain.Turma;
import br.com.frankcardoso.merenda.turma.infrastructure.EnturmacaoRepository;
import br.com.frankcardoso.merenda.turma.infrastructure.TurmaRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unico caminho de escrita de vinculos aluno-turma. Mantem o invariante de que um aluno nunca
 * tem duas enturmacoes sobrepostas: a fila consulta a turma vigente com Optional, e duas
 * linhas para o mesmo dia derrubariam a bipagem. A checagem roda sob lock pessimista na linha
 * do aluno, entao duas requisicoes concorrentes para o mesmo aluno nao passam juntas.
 */
@Service
public class EnturmacaoService {

    private final EnturmacaoRepository enturmacoes;
    private final AlunoRepository alunos;
    private final TurmaRepository turmas;

    public EnturmacaoService(EnturmacaoRepository enturmacoes, AlunoRepository alunos, TurmaRepository turmas) {
        this.enturmacoes = enturmacoes;
        this.alunos = alunos;
        this.turmas = turmas;
    }

    /** Abre um vinculo sem fim. Falha se o periodo tocar qualquer vinculo existente do aluno. */
    @Transactional
    public Enturmacao enturmar(UUID alunoId, UUID turmaId, LocalDate dataInicio) {
        Aluno aluno = alunoBloqueado(alunoId);
        Turma turma = turma(turmaId);
        garantirSemSobreposicao(alunoId, dataInicio);
        return enturmacoes.save(new Enturmacao(UUID.randomUUID(), aluno, turma, dataInicio, null, null));
    }

    /** Fecha o vinculo aberto em dataMudanca - 1 e abre o novo em dataMudanca, na mesma transacao. */
    @Transactional
    public Enturmacao transferir(UUID alunoId, UUID turmaId, LocalDate dataMudanca) {
        Aluno aluno = alunoBloqueado(alunoId);
        Turma destino = turma(turmaId);

        Enturmacao vigente = enturmacoes.findByAlunoIdOrderByDataInicioDesc(alunoId).stream()
            .filter(e -> e.getDataFim() == null)
            .findFirst()
            .orElseThrow(() -> new ConflitoCadastroException("Aluno sem vinculo vigente para transferir"));

        if (!dataMudanca.isAfter(vigente.getDataInicio())) {
            throw new DadosInvalidosException(
                "dataMudanca deve ser posterior ao inicio do vinculo vigente (%s)".formatted(vigente.getDataInicio()));
        }
        if (vigente.getTurma().getId().equals(turmaId)) {
            throw new ConflitoCadastroException("Aluno ja esta na turma de destino");
        }

        vigente.encerrar(dataMudanca.minusDays(1));
        enturmacoes.save(vigente);
        // Reavalia com o vinculo ja fechado: pega qualquer outro vinculo que alcance dataMudanca.
        garantirSemSobreposicao(alunoId, dataMudanca);
        return enturmacoes.save(new Enturmacao(UUID.randomUUID(), aluno, destino, dataMudanca, null, null));
    }

    private void garantirSemSobreposicao(UUID alunoId, LocalDate inicio) {
        boolean sobrepoe = enturmacoes.findByAlunoIdOrderByDataInicioDesc(alunoId).stream()
            .anyMatch(e -> e.sobrepoe(inicio, null));
        if (sobrepoe) {
            throw new ConflitoCadastroException(
                "Ja existe vinculo do aluno que cobre ou segue %s; use a transferencia".formatted(inicio));
        }
    }

    private Aluno alunoBloqueado(UUID alunoId) {
        return alunos.buscarParaAtualizar(alunoId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Aluno", alunoId));
    }

    private Turma turma(UUID turmaId) {
        return turmas.findById(turmaId).orElseThrow(() -> new RecursoNaoEncontradoException("Turma", turmaId));
    }
}
