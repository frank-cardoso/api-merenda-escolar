package br.com.frankcardoso.merenda.relatorio.api;

import br.com.frankcardoso.merenda.relatorio.domain.StatusRelatorioIA;
import java.time.Instant;
import java.util.UUID;

public record RelatorioResumoResponse(UUID id, StatusRelatorioIA status, Instant criadoEm,
                                      String provedor, String modelo) {}
