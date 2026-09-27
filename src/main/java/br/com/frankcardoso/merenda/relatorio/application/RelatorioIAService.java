package br.com.frankcardoso.merenda.relatorio.application;

import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaOutput;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.relatorio.api.RelatorioResumoResponse;
import br.com.frankcardoso.merenda.relatorio.api.CriarRelatorioIARequest;
import br.com.frankcardoso.merenda.relatorio.api.CriarRelatorioIAResponse;
import br.com.frankcardoso.merenda.relatorio.api.RelatorioIAResponse;
import br.com.frankcardoso.merenda.relatorio.domain.RelatorioIA;
import br.com.frankcardoso.merenda.relatorio.infrastructure.RelatorioIARepository;
import br.com.frankcardoso.merenda.medicao.infrastructure.MedicaoSobraRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RelatorioIAService {
    private static final int MINIMO_FECHAMENTOS_IA = 20;
    private static final int MINIMO_FECHAMENTOS_ITEM_IA = 3;

    private final RelatorioIARepository repository;
    private final RelatorioIAWorker worker;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MedicaoSobraRepository medicoes;

    public RelatorioIAService(RelatorioIARepository repository, RelatorioIAWorker worker,
                              ObjectMapper objectMapper, Clock clock, MedicaoSobraRepository medicoes) {
        this.repository = repository;
        this.worker = worker;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.medicoes = medicoes;
    }

    public CriarRelatorioIAResponse criar(CriarRelatorioIARequest request) {
        validarAmostra(request);
        RelatorioIA relatorio = repository.saveAndFlush(
            RelatorioIA.pendente(request.dataReferencia(), request.turno(), Instant.now(clock),
                request.receitaIds(), request.datasSelecionadas()));
        worker.processar(relatorio.getId());
        return new CriarRelatorioIAResponse(
            relatorio.getId(),
            relatorio.getStatus(),
            "/api/v1/relatorios-ia/" + relatorio.getId()
        );
    }

    private void validarAmostra(CriarRelatorioIARequest request) {
        var inicio = request.dataReferencia().minusDays(29);
        var fechamentos = request.datasSelecionadas().isEmpty()
            ? request.receitaIds().isEmpty()
                ? medicoes.countFechamentos(inicio, request.dataReferencia(), request.turno())
                : medicoes.countFechamentosPorReceitas(inicio, request.dataReferencia(),
                    request.turno(), request.receitaIds())
            : request.receitaIds().isEmpty()
                ? medicoes.countFechamentosNasDatas(request.datasSelecionadas(), request.turno())
                : medicoes.datasComFechamentoCompleto(request.datasSelecionadas(), request.turno(),
                    request.receitaIds(), request.receitaIds().size()).size();
        if (fechamentos < MINIMO_FECHAMENTOS_IA) {
            throw new IllegalArgumentException(
                "A análise IA exige pelo menos %d dias com fechamento no período"
                    .formatted(MINIMO_FECHAMENTOS_IA));
        }
        for (var receitaId : request.receitaIds()) {
            var diasDoItem = request.datasSelecionadas().isEmpty()
                ? medicoes.countDiasPorReceita(inicio, request.dataReferencia(), request.turno(), receitaId)
                : medicoes.countDiasPorReceitaNasDatas(request.datasSelecionadas(), request.turno(), receitaId);
            if (diasDoItem
                < MINIMO_FECHAMENTOS_ITEM_IA) {
                throw new IllegalArgumentException(
                    "A receita %s exige pelo menos %d dias com fechamento"
                        .formatted(receitaId, MINIMO_FECHAMENTOS_ITEM_IA));
            }
        }
    }

    public RelatorioIAResponse buscar(UUID id) {
        var relatorio = repository.findById(id)
            .orElseThrow(() -> new RelatorioNaoEncontradoException(id));
        return new RelatorioIAResponse(
            relatorio.getId(),
            relatorio.getDataReferencia(),
            relatorio.getTurno(),
            relatorio.getStatus(),
            relatorio.getProvedor(),
            relatorio.getModelo(),
            relatorio.getPromptVersao(),
            lerResultado(relatorio.getResultadoJson()),
            relatorio.getErro(),
            relatorio.getTentativas(),
            relatorio.getCriadoEm(),
            relatorio.getIniciadoEm(),
            relatorio.getConcluidoEm(),
            lerIndicadores(relatorio.getIndicadoresJson())
        );
    }

    private AnaliseLogisticaOutput lerResultado(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, AnaliseLogisticaOutput.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Resultado persistido possui formato invalido", exception);
        }
    }

    private IndicadoresLogisticos lerIndicadores(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, IndicadoresLogisticos.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Indicadores persistidos possuem formato inválido", exception);
        }
    }

    public List<RelatorioResumoResponse> listar(LocalDate data, Turno turno) {
        return repository.findTop20ByDataReferenciaAndTurnoOrderByCriadoEmDesc(data, turno).stream()
            .map(r -> new RelatorioResumoResponse(
                r.getId(), r.getStatus(), r.getCriadoEm(), r.getProvedor(), r.getModelo())).toList();
    }
}
