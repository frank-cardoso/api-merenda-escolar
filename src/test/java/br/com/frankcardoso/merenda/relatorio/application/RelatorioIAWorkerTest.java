package br.com.frankcardoso.merenda.relatorio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoOutput;
import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoPort;
import br.com.frankcardoso.merenda.analytics.application.port.TendenciaItemInput;
import br.com.frankcardoso.merenda.analytics.application.port.TendenciaItemOutput;
import br.com.frankcardoso.merenda.analytics.application.port.TendenciaItemPort;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.gestao.api.ConsolidacaoConsumoResponse;
import br.com.frankcardoso.merenda.gestao.application.ConsolidacaoConsumoService;
import br.com.frankcardoso.merenda.historico.api.ItemHistorico;
import br.com.frankcardoso.merenda.historico.api.SerieItemHistorico;
import br.com.frankcardoso.merenda.historico.application.HistoricoConsumoService;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaInput;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaOutput;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaPort;
import br.com.frankcardoso.merenda.relatorio.domain.RelatorioIA;
import br.com.frankcardoso.merenda.relatorio.infrastructure.RelatorioIARepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class RelatorioIAWorkerTest {

    private static final Instant AGORA = Instant.parse("2026-08-26T12:00:00Z");

    @Mock
    private RelatorioIARepository repository;

    @Mock
    private ConsolidacaoConsumoService consolidacaoService;

    @Mock
    private AnaliseLogisticaPort analisePort;

    @Mock
    private PrevisaoConsumoPort previsaoPort;

    @Mock
    private TendenciaItemPort tendenciaItemPort;

    @Mock
    private HistoricoConsumoService historicoService;

    @Mock
    private AuditoriaConsumoRepository auditoriaRepository;

    @Mock
    private br.com.frankcardoso.merenda.gestao.application.IndicadoresLogisticosService indicadoresService;

    private static final UUID ID_ARROZ = UUID.randomUUID();
    private static final UUID ID_FEIJAO = UUID.randomUUID();
    private static final UUID ID_FRANGO = UUID.randomUUID();
    private static final UUID ID_SALADA = UUID.randomUUID();

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);

    @Test
    void deveUsarPrevisaoDoPythonComoInsumoDaAnaliseComIa() throws Exception {
        var relatorioId = UUID.randomUUID();
        var data = LocalDate.of(2026, 8, 26);
        var relatorio = RelatorioIA.pendente(data, Turno.NOITE, AGORA);
        var consolidacao = new ConsolidacaoConsumoResponse(
            data,
            Turno.NOITE,
            UUID.randomUUID(),
            "Arroz, feijao e frango",
            List.of("arroz", "feijao", "frango"),
            List.of(ID_ARROZ, ID_FEIJAO, ID_FRANGO),
            300,
            1,
            3,
            new BigDecimal("0.33"),
            299
        );
        var previsao = new PrevisaoConsumoOutput(
            2,
            null,
            2,
            "REALIZADO_SEM_HISTORICO",
            -298,
            "NAO_AVALIAVEL",
            "Sem medicao de sobra e resto",
            "BAIXA",
            "baseline-estatistico-v2",
            List.of("Historico insuficiente")
        );

        when(repository.findById(relatorioId)).thenReturn(Optional.of(relatorio));
        when(consolidacaoService.consolidar(data, Turno.NOITE)).thenReturn(consolidacao);
        when(previsaoPort.prever(any())).thenReturn(previsao);
        // A fila recebeu outro consumo entre a primeira consulta e a fotografia de indicadores.
        var indicadores = objectMapper.readValue("""
            {"schemaVersion":"2","calculoVersao":"indicadores-v1","status":"DISPONIVEL",
             "dataReferencia":"2026-08-26","inicioHistorico":"2026-07-28","turno":"NOITE",
             "execucaoPlanejamento":{"refeicoesPlanejadas":300,"consumosRegistrados":2,
              "percentual":0.67,"metaPercentual":80,"diferencaMetaPp":-79.33,"statusMeta":"ABAIXO"},
             "atendimentos":{"alunosUnicos":2,"repeticoes":0},
             "topComidas":[],
             "porTurma":[
               {"turma":"1A","consumosRegistrados":1,"alunosUnicos":1,"repeticoes":0,
                "percentual":null,"statusMeta":"NAO_AVALIAVEL","motivo":"Sem presenca elegivel."},
               {"turma":"1B","consumosRegistrados":1,"alunosUnicos":1,"repeticoes":0,
                "percentual":null,"statusMeta":"NAO_AVALIAVEL","motivo":"Sem presenca elegivel."}],
             "avisos":[]}
            """, br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.class);
        when(indicadoresService.calcular(data, Turno.NOITE)).thenReturn(indicadores);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(analisePort.analisar(any())).thenReturn(new AnaliseLogisticaOutput(
            "Resumo",
            "VALOR_INVALIDO_DO_MODELO",
            "OUTRO_VALOR_INVALIDO",
            List.of("Evidencia"),
            List.of("Recomendacao"),
            "Limitacao"
        ));

        var worker = new RelatorioIAWorker(
            repository,
            consolidacaoService,
            analisePort,
            previsaoPort,
            tendenciaItemPort,
            historicoService,
            auditoriaRepository,
            objectMapper,
            clock,
            "gemini",
            "gemini-3.6-flash",
            30, indicadoresService
        );

        worker.processar(relatorioId);

        var captor = ArgumentCaptor.forClass(AnaliseLogisticaInput.class);
        verify(analisePort).analisar(captor.capture());
        assertThat(captor.getValue().previsaoConsumo()).isEqualTo(previsao);
        assertThat(captor.getValue().avisoPrevisaoConsumo()).isNull();
        assertThat(captor.getValue().consumosAutorizados()).isEqualTo(2);
        assertThat(captor.getValue().taxaConsumoPlanejado()).isEqualByComparingTo("0.67");
        assertThat(captor.getValue().sobraDePlanejamento()).isEqualTo(298);
        assertThat(objectMapper.readTree(relatorio.getIndicadoresJson())
            .path("execucaoPlanejamento").path("consumosRegistrados").asLong()).isEqualTo(2);

        // A tela le a fotografia inteira; o prompt recebe a projecao resumida.
        var porTurmaSalvo = objectMapper.readTree(relatorio.getIndicadoresJson()).path("porTurma");
        assertThat(porTurmaSalvo).hasSize(2);
        assertThat(captor.getValue().indicadores().porTurma()).isNull();
        assertThat(captor.getValue().indicadores().resumoTurmas().turmas()).isEqualTo(2);
        assertThat(captor.getValue().indicadores().resumoTurmas().motivos())
            .containsExactly("Sem presenca elegivel.");

        var conclusao = captor.getValue().conclusaoDeterministica();
        assertThat(conclusao.nivelAceitacao()).isEqualTo("NAO_AVALIAVEL");
        assertThat(conclusao.riscoDesperdicio()).isEqualTo("NAO_AVALIAVEL");
        assertThat(conclusao.limitacoes()).noneMatch(l -> l.startsWith("Analise por ingrediente"));
        assertThat(conclusao.afirmacoes()).contains(
            "Os registros correspondem a 0,67% do planejamento (2 de 300 refeicoes).",
            "A meta interna de execucao de 80,00% nao foi atingida, 79,33 pontos percentuais abaixo.");

        // O modelo devolveu valores fora do enum; quem classifica e o codigo, nao ele.
        var analiseSalva = objectMapper.readTree(relatorio.getResultadoJson());
        assertThat(analiseSalva.path("nivelAceitacao").asText()).isEqualTo("NAO_AVALIAVEL");
        assertThat(analiseSalva.path("riscoDesperdicio").asText()).isEqualTo("NAO_AVALIAVEL");
    }

    @Test
    void deveContinuarAnaliseComIaQuandoServicoPythonFalhar() {
        var relatorioId = UUID.randomUUID();
        var data = LocalDate.of(2026, 8, 26);
        var relatorio = RelatorioIA.pendente(data, Turno.NOITE, AGORA);
        var consolidacao = new ConsolidacaoConsumoResponse(
            data,
            Turno.NOITE,
            UUID.randomUUID(),
            "Arroz, feijao e frango",
            List.of("arroz", "feijao", "frango"),
            List.of(ID_ARROZ, ID_FEIJAO, ID_FRANGO),
            300,
            1,
            3,
            new BigDecimal("0.33"),
            299
        );

        when(repository.findById(relatorioId)).thenReturn(Optional.of(relatorio));
        when(consolidacaoService.consolidar(data, Turno.NOITE)).thenReturn(consolidacao);
        when(previsaoPort.prever(any())).thenThrow(new ResourceAccessException("Connection refused"));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(analisePort.analisar(any())).thenReturn(new AnaliseLogisticaOutput(
            "Resumo",
            "BAIXA",
            "ALTO",
            List.of("Evidencia"),
            List.of("Recomendacao"),
            "Limitacao"
        ));

        var worker = new RelatorioIAWorker(
            repository,
            consolidacaoService,
            analisePort,
            previsaoPort,
            tendenciaItemPort,
            historicoService,
            auditoriaRepository,
            objectMapper,
            clock,
            "gemini",
            "gemini-3.6-flash",
            30, indicadoresService
        );

        worker.processar(relatorioId);

        var captor = ArgumentCaptor.forClass(AnaliseLogisticaInput.class);
        verify(analisePort).analisar(captor.capture());
        assertThat(captor.getValue().previsaoConsumo()).isNull();
        assertThat(captor.getValue().avisoPrevisaoConsumo()).isEqualTo("Connection refused");
    }

    @Test
    void deveMesclarTaxaHistoricaComTendenciaCalculadaPeloPython() {
        var relatorioId = UUID.randomUUID();
        var data = LocalDate.of(2026, 8, 26);
        var relatorio = RelatorioIA.pendente(data, Turno.NOITE, AGORA);
        var consolidacao = new ConsolidacaoConsumoResponse(
            data, Turno.NOITE, UUID.randomUUID(), "Arroz, feijao e salada",
            List.of("arroz", "salada"), List.of(ID_ARROZ, ID_SALADA),
            300, 1, 3, new BigDecimal("0.33"), 299
        );
        var itensComTaxa = List.of(
            new ItemHistorico(ID_ARROZ, "Arroz branco", 20, 18, new BigDecimal("90.0")),
            new ItemHistorico(ID_SALADA, "Salada de alface", 20, 3, new BigDecimal("15.0"))
        );
        var series = List.of(
            new SerieItemHistorico(ID_ARROZ, "Arroz branco",
                List.of(new BigDecimal("88.0"), new BigDecimal("90.0"))),
            new SerieItemHistorico(ID_SALADA, "Salada de alface",
                List.of(new BigDecimal("40.0"), new BigDecimal("15.0")))
        );

        when(repository.findById(relatorioId)).thenReturn(Optional.of(relatorio));
        when(consolidacaoService.consolidar(data, Turno.NOITE)).thenReturn(consolidacao);
        when(previsaoPort.prever(any())).thenReturn(null);
        when(historicoService.itensDoCardapioComTaxa(data, Turno.NOITE, consolidacao.receitasCardapio()))
            .thenReturn(itensComTaxa);
        when(historicoService.serieDiariaDosItens(
                data, Turno.NOITE, List.of(ID_ARROZ, ID_SALADA)))
            .thenReturn(series);
        when(tendenciaItemPort.calcular(List.of(
                new TendenciaItemInput(ID_ARROZ.toString(), series.get(0).taxasExecucao()),
                new TendenciaItemInput(ID_SALADA.toString(), series.get(1).taxasExecucao()))))
            .thenReturn(List.of(
                new TendenciaItemOutput(ID_ARROZ.toString(), "ESTAVEL"),
                new TendenciaItemOutput(ID_SALADA.toString(), "QUEDA")));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(analisePort.analisar(any())).thenReturn(new AnaliseLogisticaOutput(
            "Resumo", "BAIXA", "ALTO", List.of("Evidencia"), List.of("Recomendacao"), "Limitacao"
        ));

        var worker = new RelatorioIAWorker(
            repository, consolidacaoService, analisePort, previsaoPort, tendenciaItemPort,
            historicoService, auditoriaRepository, objectMapper, clock,
            "gemini", "gemini-3.6-flash", 30, indicadoresService
        );

        worker.processar(relatorioId);

        var captor = ArgumentCaptor.forClass(AnaliseLogisticaInput.class);
        verify(analisePort).analisar(captor.capture());
        assertThat(captor.getValue().itensDoCardapioComTendencia()).containsExactly(
            new br.com.frankcardoso.merenda.inteligencia.application.port.ItemComTendencia(
                ID_ARROZ, "Arroz branco", new BigDecimal("90.0"), "ESTAVEL"),
            new br.com.frankcardoso.merenda.inteligencia.application.port.ItemComTendencia(
                ID_SALADA, "Salada de alface", new BigDecimal("15.0"), "QUEDA")
        );
    }

    @Test
    void deveSeguirSemTendenciaQuandoServicoDeTendenciaFalhar() {
        var relatorioId = UUID.randomUUID();
        var data = LocalDate.of(2026, 8, 26);
        var relatorio = RelatorioIA.pendente(data, Turno.NOITE, AGORA);
        var consolidacao = new ConsolidacaoConsumoResponse(
            data, Turno.NOITE, UUID.randomUUID(), "Arroz", List.of("arroz"), List.of(ID_ARROZ),
            300, 1, 3, new BigDecimal("0.33"), 299
        );
        var itensComTaxa = List.of(
            new ItemHistorico(ID_ARROZ, "Arroz branco", 20, 18, new BigDecimal("90.0")));

        when(repository.findById(relatorioId)).thenReturn(Optional.of(relatorio));
        when(consolidacaoService.consolidar(data, Turno.NOITE)).thenReturn(consolidacao);
        when(previsaoPort.prever(any())).thenReturn(null);
        when(historicoService.itensDoCardapioComTaxa(data, Turno.NOITE, consolidacao.receitasCardapio()))
            .thenReturn(itensComTaxa);
        when(historicoService.serieDiariaDosItens(data, Turno.NOITE, List.of(ID_ARROZ)))
            .thenReturn(List.of(new SerieItemHistorico(ID_ARROZ, "Arroz branco",
                List.of(new BigDecimal("90.0")))));
        when(tendenciaItemPort.calcular(any())).thenThrow(new RuntimeException("Connection refused"));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(analisePort.analisar(any())).thenReturn(new AnaliseLogisticaOutput(
            "Resumo", "BAIXA", "ALTO", List.of("Evidencia"), List.of("Recomendacao"), "Limitacao"
        ));

        var worker = new RelatorioIAWorker(
            repository, consolidacaoService, analisePort, previsaoPort, tendenciaItemPort,
            historicoService, auditoriaRepository, objectMapper, clock,
            "gemini", "gemini-3.6-flash", 30, indicadoresService
        );

        worker.processar(relatorioId);

        var captor = ArgumentCaptor.forClass(AnaliseLogisticaInput.class);
        verify(analisePort).analisar(captor.capture());
        assertThat(captor.getValue().itensDoCardapioComTendencia()).containsExactly(
            new br.com.frankcardoso.merenda.inteligencia.application.port.ItemComTendencia(
                ID_ARROZ, "Arroz branco", new BigDecimal("90.0"), null));
    }
}
