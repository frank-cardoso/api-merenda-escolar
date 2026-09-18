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
            300,
            1,
            3,
            new BigDecimal("0.33"),
            299
        );
        var previsao = new PrevisaoConsumoOutput(
            1,
            -299,
            "ALTO",
            "BAIXA",
            "baseline-estatistico-v1",
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
             "topComidas":[],"porTurma":[],"avisos":[]}
            """, br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos.class);
        when(indicadoresService.calcular(data, Turno.NOITE)).thenReturn(indicadores);
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
        assertThat(captor.getValue().previsaoConsumo()).isEqualTo(previsao);
        assertThat(captor.getValue().avisoPrevisaoConsumo()).isNull();
        assertThat(captor.getValue().consumosAutorizados()).isEqualTo(2);
        assertThat(captor.getValue().taxaConsumoPlanejado()).isEqualByComparingTo("0.67");
        assertThat(captor.getValue().sobraEstimada()).isEqualTo(298);
        assertThat(objectMapper.readTree(relatorio.getIndicadoresJson())
            .path("execucaoPlanejamento").path("consumosRegistrados").asLong()).isEqualTo(2);
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
            List.of("arroz", "salada"), 300, 1, 3, new BigDecimal("0.33"), 299
        );
        var itensComTaxa = List.of(
            new ItemHistorico("Arroz branco", 20, 18, new BigDecimal("90.0")),
            new ItemHistorico("Salada de alface", 20, 3, new BigDecimal("15.0"))
        );
        var series = List.of(
            new SerieItemHistorico("Arroz branco", List.of(new BigDecimal("88.0"), new BigDecimal("90.0"))),
            new SerieItemHistorico("Salada de alface", List.of(new BigDecimal("40.0"), new BigDecimal("15.0")))
        );

        when(repository.findById(relatorioId)).thenReturn(Optional.of(relatorio));
        when(consolidacaoService.consolidar(data, Turno.NOITE)).thenReturn(consolidacao);
        when(previsaoPort.prever(any())).thenReturn(null);
        when(historicoService.itensDoCardapioComTaxa(data, Turno.NOITE, consolidacao.itensCardapio()))
            .thenReturn(itensComTaxa);
        when(historicoService.serieDiariaDosItens(
                data, Turno.NOITE, List.of("Arroz branco", "Salada de alface")))
            .thenReturn(series);
        when(tendenciaItemPort.calcular(List.of(
                new TendenciaItemInput("Arroz branco", series.get(0).taxasExecucao()),
                new TendenciaItemInput("Salada de alface", series.get(1).taxasExecucao()))))
            .thenReturn(List.of(
                new TendenciaItemOutput("Arroz branco", "ESTAVEL"),
                new TendenciaItemOutput("Salada de alface", "QUEDA")));
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
                "Arroz branco", new BigDecimal("90.0"), "ESTAVEL"),
            new br.com.frankcardoso.merenda.inteligencia.application.port.ItemComTendencia(
                "Salada de alface", new BigDecimal("15.0"), "QUEDA")
        );
    }

    @Test
    void deveSeguirSemTendenciaQuandoServicoDeTendenciaFalhar() {
        var relatorioId = UUID.randomUUID();
        var data = LocalDate.of(2026, 8, 26);
        var relatorio = RelatorioIA.pendente(data, Turno.NOITE, AGORA);
        var consolidacao = new ConsolidacaoConsumoResponse(
            data, Turno.NOITE, UUID.randomUUID(), "Arroz", List.of("arroz"),
            300, 1, 3, new BigDecimal("0.33"), 299
        );
        var itensComTaxa = List.of(new ItemHistorico("Arroz branco", 20, 18, new BigDecimal("90.0")));

        when(repository.findById(relatorioId)).thenReturn(Optional.of(relatorio));
        when(consolidacaoService.consolidar(data, Turno.NOITE)).thenReturn(consolidacao);
        when(previsaoPort.prever(any())).thenReturn(null);
        when(historicoService.itensDoCardapioComTaxa(data, Turno.NOITE, consolidacao.itensCardapio()))
            .thenReturn(itensComTaxa);
        when(historicoService.serieDiariaDosItens(data, Turno.NOITE, List.of("Arroz branco")))
            .thenReturn(List.of(new SerieItemHistorico("Arroz branco", List.of(new BigDecimal("90.0")))));
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
                "Arroz branco", new BigDecimal("90.0"), null));
    }
}
