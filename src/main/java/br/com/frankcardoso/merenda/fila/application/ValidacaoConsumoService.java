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
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.turma.infrastructure.EnturmacaoRepository;
import br.com.frankcardoso.merenda.shared.config.ApplicationConfig;
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
    private static final ZoneId ZONA_OPERACIONAL = ApplicationConfig.ZONA_OPERACIONAL;

    private final AlunoRepository alunoRepository;
    private final CardapioRepository cardapioRepository;
    private final AuditoriaConsumoRepository auditoriaRepository;
    private final EnturmacaoRepository enturmacaoRepository;
    private final TurnoResolver turnoResolver;
    private final IdentificacaoFacialService identificacaoFacial;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Duration bloqueioRepeticao;

    public ValidacaoConsumoService(AlunoRepository alunoRepository, CardapioRepository cardapioRepository,
                                    AuditoriaConsumoRepository auditoriaRepository,
                                    EnturmacaoRepository enturmacaoRepository, TurnoResolver turnoResolver,
                                    IdentificacaoFacialService identificacaoFacial,
                                    TransactionTemplate transactionTemplate, Clock clock,
                                    @Value("${merenda.fila.bloqueio-repeticao:2m}")
                                    Duration bloqueioRepeticao) {
        this.alunoRepository = alunoRepository;
        this.cardapioRepository = cardapioRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.enturmacaoRepository = enturmacaoRepository;
        this.turnoResolver = turnoResolver;
        this.identificacaoFacial = identificacaoFacial;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.bloqueioRepeticao = bloqueioRepeticao;
    }

    public ValidarConsumoResponse validar(ValidarConsumoRequest request) {
        var agora = Instant.now(clock);
        var horarioLocal = agora.atZone(ZONA_OPERACIONAL);
        Turno turno;
        try {
            turno = turnoResolver.resolver(LocalTime.from(horarioLocal));
        } catch (IllegalStateException exception) {
            return ValidarConsumoResponse.bloqueado(exception.getMessage(), null);
        }

        Aluno identificado = null;
        if (request.metodoIdentificacao() == MetodoIdentificacao.FACIAL) {
            var identificacao = identificarPorRosto(request.fotoBase64());
            if (identificacao.bloqueio() != null) {
                return ValidarConsumoResponse.bloqueado(identificacao.bloqueio(), turno);
            }
            identificado = identificacao.aluno();
        }

        LocalDate data = LocalDate.from(horarioLocal);
        UUID alunoIdentificadoId = identificado == null ? null : identificado.getId();
        try {
            return transactionTemplate.execute(
                status -> validarDentroDaTransacao(request, alunoIdentificadoId, agora, turno, data));
        } catch (DataIntegrityViolationException exception) {
            return ValidarConsumoResponse.bloqueado("CONSUMO_RECENTE_BLOQUEADO", turno);
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
                                                            Instant agora, Turno turno, LocalDate data) {
        Aluno aluno = (request.metodoIdentificacao() == MetodoIdentificacao.FACIAL
            ? alunoRepository.findByIdAndAtivoTrue(alunoIdentificadoId)
            : alunoRepository.findByCodigoPublicoAndAtivoTrue(request.alunoCodigo())).orElse(null);
        if (aluno == null) return ValidarConsumoResponse.bloqueado("ALUNO_NAO_ENCONTRADO_OU_INATIVO", turno);

        Cardapio cardapio = cardapioRepository.findByDataAndTurnoAndAtivoTrue(data, turno).orElse(null);
        if (cardapio == null) return ValidarConsumoResponse.bloqueado("CARDAPIO_NAO_CONFIGURADO", turno);

        if (auditoriaRepository.existsByAlunoIdAndDataOperacionalAndTurnoAndResultadoAndInstanteGreaterThanEqual(
            aluno.getId(), data, turno, ResultadoConsumo.AUTORIZADO, agora.minus(bloqueioRepeticao))) {
            registrarBloqueio(aluno, cardapio, data, turno, agora, request, "CONSUMO_RECENTE_BLOQUEADO");
            return bloqueio(aluno, cardapio, turno, "CONSUMO_RECENTE_BLOQUEADO");
        }

        auditoriaRepository.saveAndFlush(
            novaAuditoria(aluno, cardapio, data, turno, agora, request, ResultadoConsumo.AUTORIZADO, null, null));
        return new ValidarConsumoResponse(ResultadoConsumo.AUTORIZADO, "VERDE", aluno.getId(), aluno.getNome(),
            turno, cardapio.getNomeRefeicao(), agora, null);
    }

    private void registrarBloqueio(Aluno aluno, Cardapio cardapio, LocalDate data, Turno turno, Instant agora,
                                    ValidarConsumoRequest request, String motivo) {
        auditoriaRepository.save(
            novaAuditoria(aluno, cardapio, data, turno, agora, request, ResultadoConsumo.BLOQUEADO, motivo, null));
    }

    private AuditoriaConsumo novaAuditoria(Aluno aluno, Cardapio cardapio, LocalDate data, Turno turno, Instant agora,
                                            ValidarConsumoRequest request, ResultadoConsumo resultado, String motivo,
                                            String chave) {
        var turma = enturmacaoRepository.vigenteEm(aluno.getId(), data).map(e -> e.getTurma()).orElse(null);
        return new AuditoriaConsumo(UUID.randomUUID(), aluno, cardapio, data, turno, agora,
            request.metodoIdentificacao(), resultado, motivo, UUID.randomUUID(), chave,
            turma == null ? null : turma.getId(),
            turma == null ? AuditoriaConsumo.SEM_TURMA : turma.getCodigo());
    }

    private ValidarConsumoResponse bloqueio(Aluno aluno, Cardapio cardapio, Turno turno, String motivo) {
        return new ValidarConsumoResponse(ResultadoConsumo.BLOQUEADO, "VERMELHO", aluno.getId(), aluno.getNome(),
            turno, cardapio.getNomeRefeicao(), null, motivo);
    }
}
