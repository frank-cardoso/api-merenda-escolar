package br.com.frankcardoso.merenda.fila.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.biometria.application.IdentificacaoFacial;
import br.com.frankcardoso.merenda.biometria.application.IdentificacaoFacial.Resultado;
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
import br.com.frankcardoso.merenda.refeicao.application.GradeRefeicoesService;
import br.com.frankcardoso.merenda.refeicao.domain.GradeRefeicoes;
import br.com.frankcardoso.merenda.refeicao.domain.GradeRefeicoesInvalidaException;
import br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao;
import br.com.frankcardoso.merenda.refeicao.domain.TipoRefeicao;
import br.com.frankcardoso.merenda.turma.domain.Enturmacao;
import br.com.frankcardoso.merenda.turma.domain.Turma;
import br.com.frankcardoso.merenda.turma.infrastructure.EnturmacaoRepository;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    private static final ZoneId ZONA_OPERACIONAL = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate DATA_OPERACIONAL = LocalDate.of(2026, 9, 17);
    private static final Instant AGORA = instante("12:00");
    private static final UUID ALMOCO = RefeicaoPadrao.ALMOCO.id();

    @Mock
    private AlunoRepository alunoRepository;

    @Mock
    private CardapioRepository cardapioRepository;

    @Mock
    private AuditoriaConsumoRepository auditoriaRepository;

    @Mock
    private EnturmacaoRepository enturmacaoRepository;

    @Mock
    private GradeRefeicoesService gradeRefeicoesService;

    @Mock
    private IdentificacaoFacialService identificacaoFacial;

    @Mock
    private TransactionTemplate transactionTemplate;

    private Aluno aluno;
    private Turma turmaIntegral;
    private Cardapio cardapio;
    private ValidacaoConsumoService service;

    @BeforeEach
    void setUp() {
        lenient().when(gradeRefeicoesService.grade()).thenReturn(gradePadrao());
        aluno = new Aluno(UUID.randomUUID(), "ALU-T01", "2026001", "Ana Souza", true, null, AGORA);
        turmaIntegral = new Turma(UUID.randomUUID(), "T-INT", "Turma integral", Turno.INTEGRAL, 30, null, AGORA);
        cardapio = cardapioDe(ALMOCO, "Arroz e feijão");
        service = servicoAs("12:00");
    }

    @Test
    void integralAutorizadoNoCafeENoAlmocoDoMesmoDia() {
        configurarAluno(turmaIntegral);
        var cafe = RefeicaoPadrao.CAFE_DA_MANHA.id();
        when(cardapioRepository.findByDataAndTipoRefeicaoIdAndAtivoTrue(DATA_OPERACIONAL, cafe))
            .thenReturn(Optional.of(cardapioDe(cafe, "Pão e leite")));
        when(cardapioRepository.findByDataAndTipoRefeicaoIdAndAtivoTrue(DATA_OPERACIONAL, ALMOCO))
            .thenReturn(Optional.of(cardapio));

        var noCafe = servicoAs("07:10").validar(qrCode());
        var noAlmoco = service.validar(qrCode());

        assertThat(noCafe.resultado()).isEqualTo(ResultadoConsumo.AUTORIZADO);
        assertThat(noCafe.refeicao()).isEqualTo("Café da manhã");
        assertThat(noCafe.tipoRefeicaoId()).isEqualTo(cafe);
        assertThat(noCafe.cardapio()).isEqualTo("Pão e leite");
        assertThat(noAlmoco.resultado()).isEqualTo(ResultadoConsumo.AUTORIZADO);
        assertThat(noAlmoco.refeicao()).isEqualTo("Almoço");
        var ordem = inOrder(auditoriaRepository);
        ordem.verify(auditoriaRepository).existsByAlunoIdAndDataOperacionalAndTipoRefeicaoIdAndResultadoAndInstanteGreaterThanEqual(
            aluno.getId(), DATA_OPERACIONAL, cafe, ResultadoConsumo.AUTORIZADO,
            instante("07:10").minus(Duration.ofMinutes(2)));
        ordem.verify(auditoriaRepository).existsByAlunoIdAndDataOperacionalAndTipoRefeicaoIdAndResultadoAndInstanteGreaterThanEqual(
            aluno.getId(), DATA_OPERACIONAL, ALMOCO, ResultadoConsumo.AUTORIZADO, AGORA.minus(Duration.ofMinutes(2)));
    }

    @Test
    void parcialDaManhaBarradoNoAlmoco() {
        configurarAluno(new Turma(UUID.randomUUID(), "T-MAN", "Turma da manhã", Turno.MANHA, 30, null, AGORA));

        var resposta = service.validar(qrCode());

        assertThat(resposta.resultado()).isEqualTo(ResultadoConsumo.BLOQUEADO);
        assertThat(resposta.sinal()).isEqualTo("VERMELHO");
        assertThat(resposta.motivo()).isEqualTo("REFEICAO_NAO_PREVISTA_PARA_TURMA");
        assertThat(resposta.alunoId()).isEqualTo(aluno.getId());
        assertThat(resposta.alunoNome()).isEqualTo("Ana Souza");
        assertThat(resposta.tipoRefeicaoId()).isEqualTo(ALMOCO);
        assertThat(resposta.refeicao()).isEqualTo("Almoço");
        assertThat(resposta.turnoDaTurma()).isEqualTo("MANHA");
        assertThat(resposta.turnosDaRefeicao()).containsExactly("INTEGRAL");
        verify(auditoriaRepository, never()).save(any());
        verify(auditoriaRepository, never()).saveAndFlush(any());
        verifyNoInteractions(cardapioRepository);
    }

    @Test
    void foraDeQualquerJanelaBloqueiaAntesDeIdentificar() {
        var foraDoHorario = servicoAs("14:00");

        var porQrCode = foraDoHorario.validar(qrCode());
        var porRosto = foraDoHorario.validar(facial("foto-base64", null));

        assertThat(porQrCode.resultado()).isEqualTo(ResultadoConsumo.BLOQUEADO);
        assertThat(porQrCode.motivo()).isEqualTo("FORA_DO_HORARIO_DE_REFEICAO");
        assertThat(porRosto.motivo()).isEqualTo("FORA_DO_HORARIO_DE_REFEICAO");
        verifyNoInteractions(alunoRepository, identificacaoFacial, transactionTemplate, auditoriaRepository);
    }

    @Test
    void repeticaoNaMesmaRefeicaoDentroDaJanelaBloqueia() {
        configurarAlunoECardapio(turmaIntegral);
        when(auditoriaRepository.existsByAlunoIdAndDataOperacionalAndTipoRefeicaoIdAndResultadoAndInstanteGreaterThanEqual(
            aluno.getId(), DATA_OPERACIONAL, ALMOCO, ResultadoConsumo.AUTORIZADO, AGORA.minus(Duration.ofMinutes(2))
        )).thenReturn(true);

        var resposta = service.validar(qrCode());

        assertThat(resposta.resultado()).isEqualTo(ResultadoConsumo.BLOQUEADO);
        assertThat(resposta.motivo()).isEqualTo("CONSUMO_RECENTE_BLOQUEADO");
        assertThat(resposta.refeicao()).isEqualTo("Almoço");
        verify(auditoriaRepository, never()).saveAndFlush(any());
        var captor = ArgumentCaptor.forClass(AuditoriaConsumo.class);
        verify(auditoriaRepository).save(captor.capture());
        assertThat(captor.getValue().getTipoRefeicaoId()).isEqualTo(ALMOCO);
        assertThat(captor.getValue()).hasFieldOrPropertyWithValue("resultado", ResultadoConsumo.BLOQUEADO);
    }

    @Test
    void repeticaoNaMesmaRefeicaoDepoisDaJanelaAutoriza() {
        configurarAlunoECardapio(turmaIntegral);
        when(auditoriaRepository.existsByAlunoIdAndDataOperacionalAndTipoRefeicaoIdAndResultadoAndInstanteGreaterThanEqual(
            aluno.getId(), DATA_OPERACIONAL, ALMOCO, ResultadoConsumo.AUTORIZADO, AGORA.minus(Duration.ofMinutes(2))
        )).thenReturn(false);

        var resposta = service.validar(qrCode());

        assertThat(resposta.resultado()).isEqualTo(ResultadoConsumo.AUTORIZADO);
        assertThat(resposta.registradoEm()).isEqualTo(AGORA);
        verify(auditoriaRepository).saveAndFlush(any(AuditoriaConsumo.class));
    }

    @Test
    void alunoSemTurmaUsaARefeicaoDaJanela() {
        var lancheDaManha = RefeicaoPadrao.LANCHE_DA_MANHA.id();
        configurarAluno(null);
        when(cardapioRepository.findByDataAndTipoRefeicaoIdAndAtivoTrue(DATA_OPERACIONAL, lancheDaManha))
            .thenReturn(Optional.of(cardapioDe(lancheDaManha, "Fruta")));

        var resposta = servicoAs("09:45").validar(qrCode());

        assertThat(resposta.resultado()).isEqualTo(ResultadoConsumo.AUTORIZADO);
        assertThat(resposta.refeicao()).isEqualTo("Lanche da manhã");
        var captor = ArgumentCaptor.forClass(AuditoriaConsumo.class);
        verify(auditoriaRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTurmaId()).isNull();
        assertThat(captor.getValue().getTurmaNome()).isEqualTo("Sem turma");
    }

    @Test
    void gradeELidaUmaVezPorValidacaoEFicaForaDaTransacao() {
        configurarAluno(turmaIntegral);
        when(cardapioRepository.findByDataAndTipoRefeicaoIdAndAtivoTrue(DATA_OPERACIONAL, ALMOCO))
            .thenReturn(Optional.of(cardapio));

        service.validar(qrCode());

        var ordem = inOrder(gradeRefeicoesService, transactionTemplate);
        ordem.verify(gradeRefeicoesService, times(1)).grade();
        ordem.verify(transactionTemplate).execute(any());
        verify(gradeRefeicoesService, times(1)).grade();
    }

    @Test
    void gradeInvalidaBloqueiaSemErro() {
        when(gradeRefeicoesService.grade())
            .thenThrow(new GradeRefeicoesInvalidaException("A grade de refeições está vazia."));

        var resposta = service.validar(qrCode());

        assertThat(resposta.resultado()).isEqualTo(ResultadoConsumo.BLOQUEADO);
        assertThat(resposta.motivo()).isEqualTo("GRADE_DE_REFEICOES_INVALIDA");
        verifyNoInteractions(alunoRepository, identificacaoFacial, auditoriaRepository);
    }

    @Test
    void cardapioDaRefeicaoAusenteBloqueia() {
        configurarAluno(turmaIntegral);
        when(cardapioRepository.findByDataAndTipoRefeicaoIdAndAtivoTrue(DATA_OPERACIONAL, ALMOCO))
            .thenReturn(Optional.empty());

        var resposta = service.validar(qrCode());

        assertThat(resposta.resultado()).isEqualTo(ResultadoConsumo.BLOQUEADO);
        assertThat(resposta.motivo()).isEqualTo("CARDAPIO_NAO_CONFIGURADO");
        assertThat(resposta.refeicao()).isEqualTo("Almoço");
        verify(auditoriaRepository, never()).save(any());
        verify(auditoriaRepository, never()).saveAndFlush(any());
    }

    @Test
    void auditoriaGravaIdENomeDaRefeicao() {
        configurarAlunoECardapio(turmaIntegral);

        var resposta = service.validar(qrCode());

        assertThat(resposta.tipoRefeicaoId()).isEqualTo(ALMOCO);
        assertThat(resposta.cardapio()).isEqualTo("Arroz e feijão");
        var captor = ArgumentCaptor.forClass(AuditoriaConsumo.class);
        verify(auditoriaRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTipoRefeicaoId()).isEqualTo(ALMOCO);
        assertThat(captor.getValue().getRefeicaoNome()).isEqualTo("Almoço");
    }

    @Test
    void deveBuscarAlunoComLockPessimistaParaEvitarDuplaAutorizacaoConcorrente() throws NoSuchMethodException {
        var metodo = AlunoRepository.class.getMethod("findByCodigoPublicoAndAtivoTrue", String.class);

        var lock = metodo.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void deveBuscarAlunoFacialComLockPessimistaParaEvitarDuplaAutorizacaoConcorrente() throws NoSuchMethodException {
        var metodo = AlunoRepository.class.getMethod("findByIdAndAtivoTrue", UUID.class);

        var lock = metodo.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void facialComFalhaInesperadaBloqueiaComIndisponivelSemAuditoria() {
        when(identificacaoFacial.identificar("foto-base64")).thenThrow(new IllegalStateException("inesperado"));

        assertBloqueioSemAuditoria(facial("foto-base64", null), "RECONHECIMENTO_FACIAL_INDISPONIVEL");
    }

    @Test
    void carimbaTurmaVigenteNaDataDoConsumo() {
        configurarAlunoECardapio(turmaIntegral);

        service.validar(qrCode());

        var captor = ArgumentCaptor.forClass(AuditoriaConsumo.class);
        verify(auditoriaRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTurmaNome()).isEqualTo("T-INT");
        assertThat(captor.getValue().getTurmaId()).isEqualTo(turmaIntegral.getId());
        verify(enturmacaoRepository).vigenteEm(aluno.getId(), DATA_OPERACIONAL);
    }

    @Test
    void carimbaTurmaTambemNoBloqueio() {
        configurarAlunoECardapio(turmaIntegral);
        when(auditoriaRepository.existsByAlunoIdAndDataOperacionalAndTipoRefeicaoIdAndResultadoAndInstanteGreaterThanEqual(
            aluno.getId(), DATA_OPERACIONAL, ALMOCO, ResultadoConsumo.AUTORIZADO,
            AGORA.minus(Duration.ofMinutes(2)))).thenReturn(true);

        service.validar(qrCode());

        var captor = ArgumentCaptor.forClass(AuditoriaConsumo.class);
        verify(auditoriaRepository).save(captor.capture());
        assertThat(captor.getValue().getTurmaNome()).isEqualTo("T-INT");
        assertThat(captor.getValue().getTurmaId()).isEqualTo(turmaIntegral.getId());
        verify(enturmacaoRepository).vigenteEm(aluno.getId(), DATA_OPERACIONAL);
    }

    @Test
    void carimbaSemTurmaQuandoNaoHaEnturmacaoVigente() {
        configurarAlunoECardapio(null);

        var resposta = service.validar(qrCode());

        assertThat(resposta.resultado()).isEqualTo(ResultadoConsumo.AUTORIZADO);
        var captor = ArgumentCaptor.forClass(AuditoriaConsumo.class);
        verify(auditoriaRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getTurmaId()).isNull();
        assertThat(captor.getValue().getTurmaNome()).isEqualTo("Sem turma");
    }

    @Test
    void facialReconhecidoAutorizaOAlunoIdentificado() {
        configurarFacialReconhecido(alunoReconhecido());

        var resposta = service.validar(facial("foto-base64", null));

        assertThat(resposta.resultado()).isEqualTo(ResultadoConsumo.AUTORIZADO);
        assertThat(resposta.alunoNome()).isEqualTo("Bia Lima");
        assertThat(resposta.refeicao()).isEqualTo("Almoço");
        var captor = ArgumentCaptor.forClass(AuditoriaConsumo.class);
        verify(auditoriaRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue()).hasFieldOrPropertyWithValue("metodoIdentificacao", MetodoIdentificacao.FACIAL);
    }

    @Test
    void facialIgnoraAlunoCodigoDoCorpo() {
        configurarFacialReconhecido(alunoReconhecido());

        var resposta = service.validar(facial("foto-base64", "ALU-999"));

        assertThat(resposta.resultado()).isEqualTo(ResultadoConsumo.AUTORIZADO);
        assertThat(resposta.alunoNome()).isEqualTo("Bia Lima");
        verify(alunoRepository, never()).findByCodigoPublicoAndAtivoTrue(any());
    }

    @Test
    void facialIdentificaAntesDaTransacao() {
        configurarFacialReconhecido(alunoReconhecido());

        service.validar(facial("foto-base64", null));

        var ordem = inOrder(identificacaoFacial, transactionTemplate);
        ordem.verify(identificacaoFacial).identificar("foto-base64");
        ordem.verify(transactionTemplate).execute(any());
    }

    @Test
    void facialSemFotoBloqueiaComFotoNaoInformada() {
        assertBloqueioSemAuditoria(facial(null, null), "FOTO_NAO_INFORMADA");
        assertBloqueioSemAuditoria(facial("  ", null), "FOTO_NAO_INFORMADA");
        verify(identificacaoFacial, never()).identificar(any());
    }

    @Test
    void facialSemCandidatosBloqueiaComNenhumRostoCadastrado() {
        when(identificacaoFacial.identificar("foto-base64"))
            .thenReturn(new IdentificacaoFacial(Resultado.SEM_CANDIDATOS, null));

        assertBloqueioSemAuditoria(facial("foto-base64", null), "NENHUM_ROSTO_CADASTRADO");
    }

    @Test
    void facialNaoReconhecidoBloqueia() {
        when(identificacaoFacial.identificar("foto-base64"))
            .thenReturn(new IdentificacaoFacial(Resultado.NAO_RECONHECIDO, null));

        assertBloqueioSemAuditoria(facial("foto-base64", null), "ROSTO_NAO_RECONHECIDO");
    }

    @Test
    void facialAmbiguoBloqueia() {
        when(identificacaoFacial.identificar("foto-base64"))
            .thenReturn(new IdentificacaoFacial(Resultado.AMBIGUO, alunoReconhecido()));

        assertBloqueioSemAuditoria(facial("foto-base64", null), "ROSTO_AMBIGUO");
    }

    @Test
    void facialSemRostoNaFotoBloqueia() {
        when(identificacaoFacial.identificar("foto-base64")).thenThrow(new RostoNaoDetectadoException("sem rosto"));

        assertBloqueioSemAuditoria(facial("foto-base64", null), "ROSTO_NAO_DETECTADO");
    }

    @Test
    void facialComServicoForaDoArBloqueiaComIndisponivel() {
        when(identificacaoFacial.identificar("foto-base64"))
            .thenThrow(new ReconhecimentoFacialIndisponivelException("fora do ar", null));

        assertBloqueioSemAuditoria(facial("foto-base64", null), "RECONHECIMENTO_FACIAL_INDISPONIVEL");
    }

    @Test
    void facialReconhecidoSegueBloqueioDeRepeticao() {
        var reconhecido = alunoReconhecido();
        configurarFacialReconhecido(reconhecido);
        when(auditoriaRepository.existsByAlunoIdAndDataOperacionalAndTipoRefeicaoIdAndResultadoAndInstanteGreaterThanEqual(
            reconhecido.getId(), DATA_OPERACIONAL, ALMOCO, ResultadoConsumo.AUTORIZADO,
            AGORA.minus(Duration.ofMinutes(2)))).thenReturn(true);

        var resposta = service.validar(facial("foto-base64", null));

        assertThat(resposta.resultado()).isEqualTo(ResultadoConsumo.BLOQUEADO);
        assertThat(resposta.motivo()).isEqualTo("CONSUMO_RECENTE_BLOQUEADO");
    }

    private ValidacaoConsumoService servicoAs(String horario) {
        return new ValidacaoConsumoService(
            alunoRepository,
            cardapioRepository,
            auditoriaRepository,
            enturmacaoRepository,
            new RefeicaoResolver(gradeRefeicoesService),
            identificacaoFacial,
            transactionTemplate,
            Clock.fixed(instante(horario), ZONA_OPERACIONAL),
            Duration.ofMinutes(2)
        );
    }

    private static Instant instante(String horario) {
        return DATA_OPERACIONAL.atTime(LocalTime.parse(horario)).atZone(ZONA_OPERACIONAL).toInstant();
    }

    private static GradeRefeicoes gradePadrao() {
        return GradeRefeicoes.de(List.of(
            refeicao(RefeicaoPadrao.CAFE_DA_MANHA, 1, "07:00", "08:00", Turno.INTEGRAL),
            refeicao(RefeicaoPadrao.LANCHE_DA_MANHA, 2, "09:30", "10:30", Turno.MANHA, Turno.INTEGRAL),
            refeicao(RefeicaoPadrao.ALMOCO, 3, "11:30", "13:00", Turno.INTEGRAL),
            refeicao(RefeicaoPadrao.LANCHE_DA_TARDE, 4, "15:00", "16:00", Turno.TARDE, Turno.INTEGRAL),
            refeicao(RefeicaoPadrao.LANCHE_DA_NOITE, 5, "20:00", "21:00", Turno.NOITE)));
    }

    private static TipoRefeicao refeicao(RefeicaoPadrao padrao, int ordem, String inicio, String fim,
                                         Turno... turnos) {
        return new TipoRefeicao(padrao.id(), padrao.nome(), ordem, LocalTime.parse(inicio), LocalTime.parse(fim),
            Set.of(turnos));
    }

    private static Cardapio cardapioDe(UUID tipoRefeicaoId, String nome) {
        return new Cardapio(UUID.randomUUID(), DATA_OPERACIONAL, tipoRefeicaoId, nome, "Cardápio para teste",
            "[{\"nome\":\"Arroz branco\"}]", 300, true);
    }

    private ValidarConsumoRequest qrCode() {
        return new ValidarConsumoRequest("ALU-T01", MetodoIdentificacao.QR_CODE, null);
    }

    private ValidarConsumoRequest facial(String foto, String alunoCodigo) {
        return new ValidarConsumoRequest(alunoCodigo, MetodoIdentificacao.FACIAL, foto);
    }

    private Aluno alunoReconhecido() {
        return new Aluno(UUID.randomUUID(), "ALU-T02", "2026002", "Bia Lima", true, null, AGORA);
    }

    private void configurarFacialReconhecido(Aluno reconhecido) {
        executarTransactionTemplateImediatamente();
        when(identificacaoFacial.identificar("foto-base64"))
            .thenReturn(new IdentificacaoFacial(Resultado.RECONHECIDO, reconhecido));
        when(alunoRepository.findByIdAndAtivoTrue(reconhecido.getId())).thenReturn(Optional.of(reconhecido));
        enturmar(reconhecido, turmaIntegral);
        when(cardapioRepository.findByDataAndTipoRefeicaoIdAndAtivoTrue(DATA_OPERACIONAL, ALMOCO))
            .thenReturn(Optional.of(cardapio));
    }

    private void assertBloqueioSemAuditoria(ValidarConsumoRequest request, String motivo) {
        var resposta = service.validar(request);

        assertThat(resposta.resultado()).isEqualTo(ResultadoConsumo.BLOQUEADO);
        assertThat(resposta.motivo()).isEqualTo(motivo);
        verify(auditoriaRepository, never()).save(any());
        verify(auditoriaRepository, never()).saveAndFlush(any());
    }

    private void configurarAlunoECardapio(Turma turma) {
        configurarAluno(turma);
        when(cardapioRepository.findByDataAndTipoRefeicaoIdAndAtivoTrue(DATA_OPERACIONAL, ALMOCO))
            .thenReturn(Optional.of(cardapio));
    }

    private void configurarAluno(Turma turma) {
        executarTransactionTemplateImediatamente();
        when(alunoRepository.findByCodigoPublicoAndAtivoTrue("ALU-T01")).thenReturn(Optional.of(aluno));
        enturmar(aluno, turma);
    }

    private void enturmar(Aluno enturmado, Turma turma) {
        when(enturmacaoRepository.vigenteEm(enturmado.getId(), DATA_OPERACIONAL)).thenReturn(turma == null
            ? Optional.empty()
            : Optional.of(new Enturmacao(UUID.randomUUID(), enturmado, turma, DATA_OPERACIONAL.minusDays(30), null,
                null)));
    }

    @SuppressWarnings("unchecked")
    private void executarTransactionTemplateImediatamente() {
        doAnswer(invocation -> {
            var callback = (TransactionCallback<ValidarConsumoResponse>) invocation.getArgument(0);
            return callback.doInTransaction(null);
        }).when(transactionTemplate).execute(any());
    }
}
