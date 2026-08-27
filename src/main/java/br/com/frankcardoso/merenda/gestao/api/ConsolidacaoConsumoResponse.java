package br.com.frankcardoso.merenda.gestao.api;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ConsolidacaoConsumoResponse(
    LocalDate data,
    Turno turno,
    UUID cardapioId,
    String cardapio,
    int quantidadePlanejada,
    long consumosAutorizados,
    long tentativasBloqueadas,
    BigDecimal taxaConsumoPlanejado,
    long sobraEstimada
) {
}
