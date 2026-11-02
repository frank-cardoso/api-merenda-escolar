package br.com.frankcardoso.merenda.aluno.application;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.biometria.application.port.ReconhecimentoFacialPort;
import br.com.frankcardoso.merenda.shared.config.ApplicationConfig;
import br.com.frankcardoso.merenda.shared.web.ConflitoCadastroException;
import br.com.frankcardoso.merenda.shared.web.DadosInvalidosException;
import br.com.frankcardoso.merenda.shared.web.RecursoNaoEncontradoException;
import br.com.frankcardoso.merenda.turma.application.EnturmacaoService;
import br.com.frankcardoso.merenda.turma.domain.Enturmacao;
import br.com.frankcardoso.merenda.turma.infrastructure.EnturmacaoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlunoService {

    public record AlunoDetalhe(Aluno aluno, Optional<Enturmacao> vigente, List<Enturmacao> historico) {
    }

    private final AlunoRepository alunos;
    private final EnturmacaoRepository enturmacoes;
    private final EnturmacaoService enturmacaoService;
    private final ReconhecimentoFacialPort reconhecimentoFacial;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AlunoService(AlunoRepository alunos, EnturmacaoRepository enturmacoes,
                        EnturmacaoService enturmacaoService, ReconhecimentoFacialPort reconhecimentoFacial,
                        ObjectMapper objectMapper, Clock clock) {
        this.alunos = alunos;
        this.enturmacoes = enturmacoes;
        this.enturmacaoService = enturmacaoService;
        this.reconhecimentoFacial = reconhecimentoFacial;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Aluno> listar() {
        return alunos.findAll(Sort.by("nome"));
    }

    @Transactional(readOnly = true)
    public AlunoDetalhe detalhar(UUID id) {
        Aluno aluno = alunos.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Aluno", id));
        return detalhe(aluno);
    }

    /** Turma e data de inicio opcionais: informadas juntas, criam o primeiro vinculo. */
    @Transactional
    public AlunoDetalhe criar(String codigoPublico, String matricula, String nome, boolean ativo,
                              UUID turmaId, LocalDate dataInicio) {
        if ((turmaId == null) != (dataInicio == null)) {
            throw new DadosInvalidosException("turmaId e dataInicio devem ser informados juntos");
        }
        if (alunos.existsByCodigoPublico(codigoPublico)) {
            throw new ConflitoCadastroException("Ja existe aluno com o codigo publico " + codigoPublico);
        }
        if (alunos.existsByMatricula(matricula)) {
            throw new ConflitoCadastroException("Ja existe aluno com a matricula " + matricula);
        }
        Aluno aluno = alunos.save(new Aluno(UUID.randomUUID(), codigoPublico, matricula, nome, ativo, null, Instant.now()));
        if (turmaId != null) {
            enturmacaoService.enturmar(aluno.getId(), turmaId, dataInicio);
        }
        return detalhe(aluno);
    }

    /** O codigo publico (o que vai no cartao) nao muda; a vinculacao a turma so muda por transferencia. */
    @Transactional
    public AlunoDetalhe atualizar(UUID id, String matricula, String nome, boolean ativo) {
        Aluno aluno = alunos.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Aluno", id));
        if (!aluno.getMatricula().equals(matricula) && alunos.existsByMatricula(matricula)) {
            throw new ConflitoCadastroException("Ja existe aluno com a matricula " + matricula);
        }
        aluno.atualizar(matricula, nome, ativo);
        return detalhe(aluno);
    }

    /** Gera o embedding da foto de referencia e guarda so o vetor; a foto nunca chega a este metodo gravada em disco. */
    @Transactional
    public AlunoDetalhe cadastrarRosto(UUID id, String fotoBase64) {
        Aluno aluno = alunos.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Aluno", id));
        var embedding = reconhecimentoFacial.gerarEmbedding(fotoBase64);
        aluno.cadastrarRosto(escreverJson(embedding));
        return detalhe(aluno);
    }

    private String escreverJson(List<Float> embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Nao foi possivel serializar o embedding facial", exception);
        }
    }

    private AlunoDetalhe detalhe(Aluno aluno) {
        List<Enturmacao> historico = enturmacoes.findByAlunoIdOrderByDataInicioDesc(aluno.getId());
        Optional<Enturmacao> vigente = historico.stream().filter(e -> e.vigenteEm(LocalDate.now(clock.withZone(ApplicationConfig.ZONA_OPERACIONAL)))).findFirst();
        return new AlunoDetalhe(aluno, vigente, historico);
    }
}
