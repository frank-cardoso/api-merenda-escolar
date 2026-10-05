package br.com.frankcardoso.merenda.cardapio.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * {@code receitaId} e a identidade do item; {@code nome} e rotulo.
 *
 * No cadastro o id e opcional: quando vem nulo, o servico resolve pelo nome exato do catalogo e
 * recusa nome desconhecido. Isso mantem o formulario atual funcionando sem reintroduzir casamento
 * aproximado — o que fica gravado sempre tem id.
 */
public record ItemCardapioDto(
    UUID receitaId,
    @NotBlank @Size(max = 120) String nome,
    @Size(max = 50) String quantidade
) {
}
