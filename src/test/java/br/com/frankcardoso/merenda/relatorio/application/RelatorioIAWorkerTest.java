package br.com.frankcardoso.merenda.relatorio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoOutput;
import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoPort;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.gestao.api.ConsolidacaoConsumoResponse;
import br.com.frankcardoso.merenda.gestao.application.ConsolidacaoConsumoService;
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

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);

    @Test
    void deveUsarPrevisaoDoPythonComoInsumoDaAnaliseComIa() {
        var relatorioId = UUID.randomUUID();
        var data = LocalDate.of(2026, 8, 26);
        var relatorio = RelatorioIA.pendente(data, Turno.NOITE, AGORA);
        var consolidacao = new ConsolidacaoConsumoResponse(
            data,
            Turno.NOITE,
            UUID.randomUUID(),
            "Arroz, feijao e frango",
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
            objectMapper,
            clock,
            "gemini",
            "gemini-3.6-flash",
            30
        );

        worker.processar(relatorioId);

        var captor = ArgumentCaptor.forClass(AnaliseLogisticaInput.class);
        verify(analisePort).analisar(captor.capture());
        assertThat(captor.getValue().previsaoConsumo()).isEqualTo(previsao);
        assertThat(captor.getValue().avisoPrevisaoConsumo()).isNull();
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
            objectMapper,
            clock,
            "gemini",
            "gemini-3.6-flash",
            30
        );

        worker.processar(relatorioId);

        var captor = ArgumentCaptor.forClass(AnaliseLogisticaInput.class);
        verify(analisePort).analisar(captor.capture());
        assertThat(captor.getValue().previsaoConsumo()).isNull();
        assertThat(captor.getValue().avisoPrevisaoConsumo()).isEqualTo("Connection refused");
    }
}
