package br.com.frankcardoso.merenda.gestao.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ConsolidacaoConsumoResponse(
    LocalDate data,
    UUID tipoRefeicaoId,
    String refeicao,
    UUID cardapioId,
    String cardapio,
    List<String> itensCardapio,
    List<UUID> receitasCardapio,
    int quantidadePlanejada,
    long consumosAutorizados,
    long tentativasBloqueadas,
    BigDecimal taxaConsumoPlanejado,
    long sobraEstimada
) {
}
