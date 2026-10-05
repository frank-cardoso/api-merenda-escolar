package br.com.frankcardoso.merenda.cardapio.api;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CardapioResponse(
    UUID id,
    LocalDate data,
    Turno turno,
    String nomeRefeicao,
    String descricao,
    List<ItemCardapioDto> itens,
    int quantidadePlanejada,
    boolean ativo
) {
}
