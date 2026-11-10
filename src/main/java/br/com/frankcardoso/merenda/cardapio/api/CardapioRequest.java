package br.com.frankcardoso.merenda.cardapio.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CardapioRequest(
    @NotNull LocalDate data,
    @NotNull UUID tipoRefeicaoId,
    @NotBlank @Size(max = 150) String nomeRefeicao,
    @Size(max = 500) String descricao,
    @NotEmpty(message = "informe ao menos um item") @Valid List<ItemCardapioDto> itens,
    @NotNull @PositiveOrZero Integer quantidadePlanejada
) {
}
