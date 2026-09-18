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

    private final RelatorioIARepository repository;
    private final RelatorioIAWorker worker;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RelatorioIAService(RelatorioIARepository repository, RelatorioIAWorker worker,
                              ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.worker = worker;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public CriarRelatorioIAResponse criar(CriarRelatorioIARequest request) {
        RelatorioIA relatorio = repository.saveAndFlush(
            RelatorioIA.pendente(request.dataReferencia(), request.turno(), Instant.now(clock)));
        worker.processar(relatorio.getId());
        return new CriarRelatorioIAResponse(
            relatorio.getId(),
            relatorio.getStatus(),
            "/api/v1/relatorios-ia/" + relatorio.getId()
        );
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
