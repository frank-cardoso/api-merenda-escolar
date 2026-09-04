package br.com.frankcardoso.merenda.gestao.api;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ConsolidacaoConsumoResponse(
    LocalDate data,
    Turno turno,
    UUID cardapioId,
    String cardapio,
    List<String> itensCardapio,
    int quantidadePlanejada,
    long consumosAutorizados,
    long tentativasBloqueadas,
    BigDecimal taxaConsumoPlanejado,
    long sobraEstimada
) {
}
