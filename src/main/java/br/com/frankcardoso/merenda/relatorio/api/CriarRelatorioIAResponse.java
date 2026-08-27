package br.com.frankcardoso.merenda.relatorio.api;

import br.com.frankcardoso.merenda.relatorio.domain.StatusRelatorioIA;
import java.util.UUID;

public record CriarRelatorioIAResponse(
    UUID relatorioId,
    StatusRelatorioIA status,
    String statusUrl
) {
}
