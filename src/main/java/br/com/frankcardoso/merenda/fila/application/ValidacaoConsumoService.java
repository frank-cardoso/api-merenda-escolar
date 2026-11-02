package br.com.frankcardoso.merenda.fila.application;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.biometria.application.port.ReconhecimentoFacialPort;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ValidacaoConsumoService {

    private static final ZoneId ZONA_OPERACIONAL = ApplicationConfig.ZONA_OPERACIONAL;

    private final AlunoRepository alunoRepository;
    private final CardapioRepository cardapioRepository;
    private final AuditoriaConsumoRepository auditoriaRepository;
    private final EnturmacaoRepository enturmacaoRepository;
    private final TurnoResolver turnoResolver;
    private final ReconhecimentoFacialPort reconhecimentoFacial;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Duration bloqueioRepeticao;

    public ValidacaoConsumoService(AlunoRepository alunoRepository, CardapioRepository cardapioRepository,
                                    AuditoriaConsumoRepository auditoriaRepository,
                                    EnturmacaoRepository enturmacaoRepository, TurnoResolver turnoResolver,
                                    ReconhecimentoFacialPort reconhecimentoFacial, ObjectMapper objectMapper,
                                    TransactionTemplate transactionTemplate, Clock clock,
                                    @Value("${merenda.fila.bloqueio-repeticao:2m}")
                                    Duration bloqueioRepeticao) {
        this.alunoRepository = alunoRepository;
        this.cardapioRepository = cardapioRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.enturmacaoRepository = enturmacaoRepository;
        this.turnoResolver = turnoResolver;
        this.reconhecimentoFacial = reconhecimentoFacial;
        this.objectMapper = objectMapper;
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

        LocalDate data = LocalDate.from(horarioLocal);
        try {
            return transactionTemplate.execute(status -> validarDentroDaTransacao(request, agora, turno, data));
        } catch (DataIntegrityViolationException exception) {
            return ValidarConsumoResponse.bloqueado("CONSUMO_RECENTE_BLOQUEADO", turno);
        }
    }

    private ValidarConsumoResponse validarDentroDaTransacao(ValidarConsumoRequest request, Instant agora, Turno turno,
                                                            LocalDate data) {
        Aluno aluno = alunoRepository.findByCodigoPublicoAndAtivoTrue(request.alunoCodigo()).orElse(null);
        if (aluno == null) return ValidarConsumoResponse.bloqueado("ALUNO_NAO_ENCONTRADO_OU_INATIVO", turno);

        if (request.metodoIdentificacao() == MetodoIdentificacao.FACIAL) {
            String motivoFacial = conferirRosto(aluno, request.fotoBase64());
            if (motivoFacial != null) {
                return new ValidarConsumoResponse(ResultadoConsumo.BLOQUEADO, "VERMELHO", aluno.getId(),
                    aluno.getNome(), turno, null, null, motivoFacial);
            }
        }

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

    /** @return motivo do bloqueio, ou {@code null} se o rosto confere e a fila pode seguir. */
    private String conferirRosto(Aluno aluno, String fotoBase64) {
        if (fotoBase64 == null || fotoBase64.isBlank()) return "FOTO_NAO_INFORMADA";
        if (!aluno.temRostoCadastrado()) return "ALUNO_SEM_ROSTO_CADASTRADO";

        List<Float> embeddingCadastrado = lerEmbedding(aluno.getRostoEmbeddingJson());
        try {
            var resultado = reconhecimentoFacial.verificar(fotoBase64, embeddingCadastrado);
            return resultado.confere() ? null : "ROSTO_NAO_CONFERE";
        } catch (RostoNaoDetectadoException exception) {
            return "ROSTO_NAO_DETECTADO";
        }
    }

    private List<Float> lerEmbedding(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Float>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Embedding facial persistido em formato invalido", exception);
        }
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
