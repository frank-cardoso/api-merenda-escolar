package br.com.frankcardoso.merenda.cardapio.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ItemCardapioDto(
    @NotBlank @Size(max = 100) String nome,
    @Size(max = 50) String quantidade
) {
}
