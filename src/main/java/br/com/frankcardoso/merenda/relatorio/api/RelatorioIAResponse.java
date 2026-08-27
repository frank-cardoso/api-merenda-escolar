package br.com.frankcardoso.merenda.relatorio.api;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaOutput;
import br.com.frankcardoso.merenda.relatorio.domain.StatusRelatorioIA;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RelatorioIAResponse(
    UUID id,
    LocalDate dataReferencia,
    Turno turno,
    StatusRelatorioIA status,
    String provedor,
    String modelo,
    String promptVersao,
    AnaliseLogisticaOutput resultado,
    String erro,
    int tentativas,
    Instant criadoEm,
    Instant iniciadoEm,
    Instant concluidoEm
) {
}
