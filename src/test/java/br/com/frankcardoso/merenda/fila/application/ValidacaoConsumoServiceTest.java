package br.com.frankcardoso.merenda.fila.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.fila.api.ValidarConsumoRequest;
import br.com.frankcardoso.merenda.fila.api.ValidarConsumoResponse;
import br.com.frankcardoso.merenda.fila.domain.AuditoriaConsumo;
import br.com.frankcardoso.merenda.fila.domain.MetodoIdentificacao;
import br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.turma.domain.Enturmacao;
import br.com.frankcardoso.merenda.turma.domain.Turma;
import br.com.frankcardoso.merenda.turma.infrastructure.EnturmacaoRepository;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ValidacaoConsumoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-09-17T21:00:00Z");
    private static final ZoneId ZONA_OPERACIONAL = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate DATA_OPERACIONAL = LocalDate.of(2026, 9, 17);

    @Mock
    private AlunoRepository alunoRepository;

    @Mock
    private CardapioRepository cardapioRepository;

    @Mock
    private AuditoriaConsumoRepository auditoriaRepository;

    @Mock
    private EnturmacaoRepository enturmacaoRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    private final TurnoResolver turnoResolver = new TurnoResolver();
    private final Clock clock = Clock.fixed(AGORA, ZONA_OPERACIONAL);

    private Aluno aluno;
    private Cardapio cardapio;
    private ValidacaoConsumoService service;

    @BeforeEach
    void setUp() {
        aluno = new Aluno(
            UUID.randomUUID(),
            "ALU-001",
            "2026001",
            "Ana Souza",
            true,
            null,
            AGORA
        );
        cardapio = new Cardapio(
            UUID.randomUUID(),
            DATA_OPERACIONAL,
            Turno.NOITE,
            "Jantar demonstrativo",
            "Cardapio para teste",
            "[{\"nome\":\"Arroz branco\"}]",
            300,
            true
        );
        service = new ValidacaoConsumoService(
            alunoRepository,
            cardapioRepository,
            auditoriaRepository,
            enturmacaoRepository,
            turnoResolver,
            transactionTemplate,
            clock,
            Duration.ofMinutes(2)
        );
    }

    @Test
    void deveBloquearRepeticaoDoAlunoDentroDaJanelaDeDoisMinutosNoMesmoTurno() {
        configurarAlunoECardapio();
        when(auditoriaRepository.existsByAlunoIdAndDataOperacionalAndTurnoAndResultadoAndInstanteGreaterThanEqual(
            aluno.getId(),
            DATA_OPERACIONAL,
            Turno.NOITE,
            ResultadoConsumo.AUTORIZADO,
            AGORA.minus(Duration.ofMinutes(2))
        )).thenReturn(true);

        var resposta = service.validar(new ValidarConsumoRequest("ALU-001", MetodoIdentificacao.QR_CODE));

        assertThat(resposta.resultado()).isEqualTo(ResultadoConsumo.BLOQUEADO);
        assertThat(resposta.motivo()).isEqualTo("CONSUMO_RECENTE_BLOQUEADO");
        verify(auditoriaRepository, never()).saveAndFlush(any());
        verify(auditoriaRepository).save(any(AuditoriaConsumo.class));
    }

    @Test
    void deveAutorizarRepeticaoDoAlunoDepoisDaJanelaDeDoisMinutosNoMesmoTurno() {
        configurarAlunoECardapio();
        when(auditoriaRepository.existsByAlunoIdAndDataOperacionalAndTurnoAndResultadoAndInstanteGreaterThanEqual(
            aluno.getId(),
            DATA_OPERACIONAL,
            Turno.NOITE,
            ResultadoConsumo.AUTORIZADO,
            AGORA.minus(Duration.ofMinutes(2))
        )).thenReturn(false);

        var resposta = service.validar(new ValidarConsumoRequest("ALU-001", MetodoIdentificacao.QR_CODE));

        assertThat(resposta.resultado()).isEqualTo(ResultadoConsumo.AUTORIZADO);
        assertThat(resposta.registradoEm()).isEqualTo(AGORA);

        var captor = ArgumentCaptor.forClass(AuditoriaConsumo.class);
        verify(auditoriaRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void deveBuscarAlunoComLockPessimistaParaEvitarDuplaAutorizacaoConcorrente() throws NoSuchMethodException {
        var metodo = AlunoRepository.class.getMethod("findByCodigoPublicoAndAtivoTrue", String.class);

        var lock = metodo.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void carimbaTurmaVigenteNaDataDoConsumo() {
        configurarAlunoECardapio();
        var turma = new Turma(UUID.randomUUID(), "8A", "8o ano A", Turno.NOITE, 30, null, AGORA);
        when(enturmacaoRepository.vigenteEm(aluno.getId(), DATA_OPERACIONAL)).thenReturn(Optional.of(
            new Enturmacao(UUID.randomUUID(), aluno, turma, DATA_OPERACIONAL.minusDays(30), null, null)));

        service.validar(new ValidarConsumoRequest("ALU-001", MetodoIdentificacao.QR_CODE));

        var captor = ArgumentCaptor.forClass(AuditoriaConsumo.class);
        verify(auditoriaRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTurmaNome()).isEqualTo("8A");
        assertThat(captor.getValue().getTurmaId()).isEqualTo(turma.getId());
    }

    @Test
    void carimbaTurmaTambemNoBloqueio() {
        configurarAlunoECardapio();
        var turma = new Turma(UUID.randomUUID(), "8A", "8o ano A", Turno.NOITE, 30, null, AGORA);
        when(enturmacaoRepository.vigenteEm(aluno.getId(), DATA_OPERACIONAL)).thenReturn(Optional.of(
            new Enturmacao(UUID.randomUUID(), aluno, turma, DATA_OPERACIONAL.minusDays(30), null, null)));
        when(auditoriaRepository.existsByAlunoIdAndDataOperacionalAndTurnoAndResultadoAndInstanteGreaterThanEqual(
            aluno.getId(), DATA_OPERACIONAL, Turno.NOITE, ResultadoConsumo.AUTORIZADO,
            AGORA.minus(Duration.ofMinutes(2)))).thenReturn(true);

        service.validar(new ValidarConsumoRequest("ALU-001", MetodoIdentificacao.QR_CODE));

        var captor = ArgumentCaptor.forClass(AuditoriaConsumo.class);
        verify(auditoriaRepository).save(captor.capture());
        assertThat(captor.getValue().getTurmaNome()).isEqualTo("8A");
        assertThat(captor.getValue().getTurmaId()).isEqualTo(turma.getId());
    }

    @Test
    void carimbaSemTurmaQuandoNaoHaEnturmacaoVigente() {
        configurarAlunoECardapio();
        when(enturmacaoRepository.vigenteEm(aluno.getId(), DATA_OPERACIONAL)).thenReturn(Optional.empty());

        service.validar(new ValidarConsumoRequest("ALU-001", MetodoIdentificacao.QR_CODE));

        var captor = ArgumentCaptor.forClass(AuditoriaConsumo.class);
        verify(auditoriaRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTurmaId()).isNull();
        assertThat(captor.getValue().getTurmaNome()).isEqualTo("Sem turma");
    }

    @Test
    void autorizaMesmoSemEnturmacaoVigente() {
        configurarAlunoECardapio();
        when(enturmacaoRepository.vigenteEm(aluno.getId(), DATA_OPERACIONAL)).thenReturn(Optional.empty());

        var resposta = service.validar(new ValidarConsumoRequest("ALU-001", MetodoIdentificacao.QR_CODE));

        assertThat(resposta.resultado()).isEqualTo(ResultadoConsumo.AUTORIZADO);
    }

    private void configurarAlunoECardapio() {
        executarTransactionTemplateImediatamente();
        when(alunoRepository.findByCodigoPublicoAndAtivoTrue("ALU-001")).thenReturn(Optional.of(aluno));
        when(cardapioRepository.findByDataAndTurnoAndAtivoTrue(DATA_OPERACIONAL, Turno.NOITE))
            .thenReturn(Optional.of(cardapio));
    }

    @SuppressWarnings("unchecked")
    private void executarTransactionTemplateImediatamente() {
        doAnswer(invocation -> {
            var callback = (TransactionCallback<ValidarConsumoResponse>) invocation.getArgument(0);
            return callback.doInTransaction(null);
        }).when(transactionTemplate).execute(any());
    }
}
