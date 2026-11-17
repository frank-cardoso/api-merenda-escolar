package br.com.frankcardoso.merenda.fila.application;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.biometria.application.IdentificacaoFacial;
import br.com.frankcardoso.merenda.biometria.application.IdentificacaoFacialService;
import br.com.frankcardoso.merenda.biometria.domain.ReconhecimentoFacialIndisponivelException;
import br.com.frankcardoso.merenda.biometria.domain.RostoNaoDetectadoException;
import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.fila.api.ValidarConsumoRequest;
import br.com.frankcardoso.merenda.fila.api.ValidarConsumoResponse;
import br.com.frankcardoso.merenda.fila.domain.AuditoriaConsumo;
import br.com.frankcardoso.merenda.fila.domain.MetodoIdentificacao;
import br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.refeicao.domain.GradeRefeicoes;
import br.com.frankcardoso.merenda.refeicao.domain.ResolucaoRefeicao;
import br.com.frankcardoso.merenda.refeicao.domain.TipoRefeicao;
import br.com.frankcardoso.merenda.shared.config.ApplicationConfig;
import br.com.frankcardoso.merenda.turma.domain.Turma;
import br.com.frankcardoso.merenda.turma.infrastructure.EnturmacaoRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ValidacaoConsumoService {

    private static final Logger LOG = LoggerFactory.getLogger(ValidacaoConsumoService.class);
    private static final String GRADE_INVALIDA = "GRADE_DE_REFEICOES_INVALIDA";
    private static final ZoneId ZONA_OPERACIONAL = ApplicationConfig.ZONA_OPERACIONAL;

    private final AlunoRepository alunoRepository;
    private final CardapioRepository cardapioRepository;
    private final AuditoriaConsumoRepository auditoriaRepository;
    private final EnturmacaoRepository enturmacaoRepository;
    private final RefeicaoResolver refeicaoResolver;
    private final IdentificacaoFacialService identificacaoFacial;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Duration bloqueioRepeticao;

    public ValidacaoConsumoService(AlunoRepository alunoRepository, CardapioRepository cardapioRepository,
                                    AuditoriaConsumoRepository auditoriaRepository,
                                    EnturmacaoRepository enturmacaoRepository, RefeicaoResolver refeicaoResolver,
                                    IdentificacaoFacialService identificacaoFacial,
                                    TransactionTemplate transactionTemplate, Clock clock,
                                    @Value("${merenda.fila.bloqueio-repeticao:2m}")
                                    Duration bloqueioRepeticao) {
        this.alunoRepository = alunoRepository;
        this.cardapioRepository = cardapioRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.enturmacaoRepository = enturmacaoRepository;
        this.refeicaoResolver = refeicaoResolver;
        this.identificacaoFacial = identificacaoFacial;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.bloqueioRepeticao = bloqueioRepeticao;
    }

    public ValidarConsumoResponse validar(ValidarConsumoRequest request) {
        var agora = Instant.now(clock);
        var horarioLocal = agora.atZone(ZONA_OPERACIONAL);
        LocalTime horario = LocalTime.from(horarioLocal);
        var gradeCarregada = refeicaoResolver.carregarGrade();
        if (gradeCarregada.isEmpty()) return ValidarConsumoResponse.bloqueado(GRADE_INVALIDA);
        GradeRefeicoes grade = gradeCarregada.get();
        var foraDoHorario = grade.bloqueioDoHorario(horario);
        if (foraDoHorario.isPresent()) return ValidarConsumoResponse.bloqueado(foraDoHorario.get());

        Aluno identificado = null;
        if (request.metodoIdentificacao() == MetodoIdentificacao.FACIAL) {
            var identificacao = identificarPorRosto(request.fotoBase64());
            if (identificacao.bloqueio() != null) {
                return ValidarConsumoResponse.bloqueado(identificacao.bloqueio());
            }
            identificado = identificacao.aluno();
        }

        LocalDate data = LocalDate.from(horarioLocal);
        UUID alunoIdentificadoId = identificado == null ? null : identificado.getId();
        try {
            return transactionTemplate.execute(
                status -> validarDentroDaTransacao(request, alunoIdentificadoId, grade, agora, horario, data));
        } catch (DataIntegrityViolationException exception) {
            return ValidarConsumoResponse.bloqueado("CONSUMO_RECENTE_BLOQUEADO");
        }
    }

    /** Resultado da identificacao facial: ou o aluno, ou o motivo do bloqueio. */
    private record IdentificacaoNaFila(Aluno aluno, String bloqueio) {
    }

    private IdentificacaoNaFila identificarPorRosto(String fotoBase64) {
        if (fotoBase64 == null || fotoBase64.isBlank()) return new IdentificacaoNaFila(null, "FOTO_NAO_INFORMADA");
        try {
            IdentificacaoFacial identificacao = identificacaoFacial.identificar(fotoBase64);
            return switch (identificacao.resultado()) {
                case RECONHECIDO -> new IdentificacaoNaFila(identificacao.aluno(), null);
                case SEM_CANDIDATOS -> new IdentificacaoNaFila(null, "NENHUM_ROSTO_CADASTRADO");
                case NAO_RECONHECIDO -> new IdentificacaoNaFila(null, "ROSTO_NAO_RECONHECIDO");
                case AMBIGUO -> new IdentificacaoNaFila(null, "ROSTO_AMBIGUO");
            };
        } catch (RostoNaoDetectadoException exception) {
            return new IdentificacaoNaFila(null, "ROSTO_NAO_DETECTADO");
        } catch (ReconhecimentoFacialIndisponivelException exception) {
            LOG.warn("Identificacao facial indisponivel", exception);
            return new IdentificacaoNaFila(null, "RECONHECIMENTO_FACIAL_INDISPONIVEL");
        } catch (RuntimeException exception) {
            LOG.warn("Falha inesperada na identificacao facial", exception);
            return new IdentificacaoNaFila(null, "RECONHECIMENTO_FACIAL_INDISPONIVEL");
        }
    }

    private ValidarConsumoResponse validarDentroDaTransacao(ValidarConsumoRequest request, UUID alunoIdentificadoId,
                                                            GradeRefeicoes grade, Instant agora, LocalTime horario,
                                                            LocalDate data) {
        Aluno aluno = (request.metodoIdentificacao() == MetodoIdentificacao.FACIAL
            ? alunoRepository.findByIdAndAtivoTrue(alunoIdentificadoId)
            : alunoRepository.findByCodigoPublicoAndAtivoTrue(request.alunoCodigo())).orElse(null);
        if (aluno == null) return ValidarConsumoResponse.bloqueado("ALUNO_NAO_ENCONTRADO_OU_INATIVO");

        Turma turma = enturmacaoRepository.vigenteEm(aluno.getId(), data).map(e -> e.getTurma()).orElse(null);
        var resolucao = grade.resolver(horario, turma == null ? null : turma.getTurno());
        if (resolucao.refeicaoNaJanela() != null) return bloqueioPorTurma(aluno, turma, resolucao);
        if (resolucao.bloqueada()) return bloqueio(aluno, null, null, resolucao.motivoBloqueio());
        TipoRefeicao refeicao = resolucao.refeicao();

        Cardapio cardapio = cardapioRepository.findByDataAndTipoRefeicaoIdAndAtivoTrue(data, refeicao.getId())
            .orElse(null);
        if (cardapio == null) return bloqueio(aluno, refeicao, null, "CARDAPIO_NAO_CONFIGURADO");

        var consumo = new Consumo(aluno, turma, refeicao, cardapio, data, agora, request);
        // Com teto em "agora": passagem gravada adiante do relógio (base de outra execução com
        // merenda.demo.horario) não pode prender o aluno até o relógio alcançá-la.
        if (auditoriaRepository.existsByAlunoIdAndDataOperacionalAndTipoRefeicaoIdAndResultadoAndInstanteBetween(
            aluno.getId(), data, refeicao.getId(), ResultadoConsumo.AUTORIZADO, agora.minus(bloqueioRepeticao), agora)) {
            auditoriaRepository.save(consumo.auditoria(ResultadoConsumo.BLOQUEADO, "CONSUMO_RECENTE_BLOQUEADO"));
            return bloqueio(aluno, refeicao, cardapio, "CONSUMO_RECENTE_BLOQUEADO");
        }

        auditoriaRepository.saveAndFlush(consumo.auditoria(ResultadoConsumo.AUTORIZADO, null));
        return new ValidarConsumoResponse(ResultadoConsumo.AUTORIZADO, "VERDE", aluno.getId(), aluno.getNome(),
            refeicao.getId(), refeicao.getNome(), cardapio.getNomeRefeicao(), agora, null, null, null);
    }

    /** Passagem já resolvida (aluno, turma vigente, refeição e cardápio), pronta para virar auditoria. */
    private record Consumo(Aluno aluno, Turma turma, TipoRefeicao refeicao, Cardapio cardapio, LocalDate data,
                           Instant agora, ValidarConsumoRequest request) {

        AuditoriaConsumo auditoria(ResultadoConsumo resultado, String motivo) {
            return new AuditoriaConsumo(UUID.randomUUID(), aluno, cardapio, data, refeicao.getId(),
                refeicao.getNome(), agora, request.metodoIdentificacao(), resultado, motivo, UUID.randomUUID(), null,
                turma == null ? null : turma.getId(),
                turma == null ? AuditoriaConsumo.SEM_TURMA : turma.getCodigo());
        }
    }

    private ValidarConsumoResponse bloqueio(Aluno aluno, TipoRefeicao refeicao, Cardapio cardapio, String motivo) {
        return new ValidarConsumoResponse(ResultadoConsumo.BLOQUEADO, "VERMELHO", aluno.getId(), aluno.getNome(),
            refeicao == null ? null : refeicao.getId(), refeicao == null ? null : refeicao.getNome(),
            cardapio == null ? null : cardapio.getNomeRefeicao(), null, motivo, null, null);
    }

    private ValidarConsumoResponse bloqueioPorTurma(Aluno aluno, Turma turma, ResolucaoRefeicao resolucao) {
        var naJanela = resolucao.refeicaoNaJanela();
        return new ValidarConsumoResponse(ResultadoConsumo.BLOQUEADO, "VERMELHO", aluno.getId(), aluno.getNome(),
            naJanela.getId(), naJanela.getNome(), null, null, resolucao.motivoBloqueio(),
            turma.getTurno().name(), naJanela.getTurnos().stream().sorted().map(Enum::name).toList());
    }
}
