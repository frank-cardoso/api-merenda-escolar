package br.com.frankcardoso.merenda.cardapio.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CardapioResponse(
    UUID id,
    LocalDate data,
    UUID tipoRefeicaoId,
    String refeicao,
    String nomeRefeicao,
    String descricao,
    List<ItemCardapioDto> itens,
    int quantidadePlanejada,
    boolean ativo
) {
}
