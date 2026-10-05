package br.com.frankcardoso.merenda.fila.application;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.fila.api.ValidarConsumoRequest;
import br.com.frankcardoso.merenda.fila.api.ValidarConsumoResponse;
import br.com.frankcardoso.merenda.fila.domain.AuditoriaConsumo;
import br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ValidacaoConsumoService {

    private static final ZoneId ZONA_OPERACIONAL = ZoneId.of("America/Sao_Paulo");

    private final AlunoRepository alunoRepository;
    private final CardapioRepository cardapioRepository;
    private final AuditoriaConsumoRepository auditoriaRepository;
    private final TurnoResolver turnoResolver;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Duration bloqueioRepeticao;

    public ValidacaoConsumoService(AlunoRepository alunoRepository, CardapioRepository cardapioRepository,
                                    AuditoriaConsumoRepository auditoriaRepository, TurnoResolver turnoResolver,
                                    TransactionTemplate transactionTemplate, Clock clock,
                                    @Value("${merenda.fila.bloqueio-repeticao:2m}")
                                    Duration bloqueioRepeticao) {
        this.alunoRepository = alunoRepository;
        this.cardapioRepository = cardapioRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.turnoResolver = turnoResolver;
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
        return new AuditoriaConsumo(UUID.randomUUID(), aluno, cardapio, data, turno, agora,
            request.metodoIdentificacao(), resultado, motivo, UUID.randomUUID(), chave);
    }

    private ValidarConsumoResponse bloqueio(Aluno aluno, Cardapio cardapio, Turno turno, String motivo) {
        return new ValidarConsumoResponse(ResultadoConsumo.BLOQUEADO, "VERMELHO", aluno.getId(), aluno.getNome(),
            turno, cardapio.getNomeRefeicao(), null, motivo);
    }
}
