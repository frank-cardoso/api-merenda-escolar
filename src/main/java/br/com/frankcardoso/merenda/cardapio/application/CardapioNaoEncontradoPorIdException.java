package br.com.frankcardoso.merenda.cardapio.application;

import java.util.UUID;

public class CardapioNaoEncontradoPorIdException extends RuntimeException {

    public CardapioNaoEncontradoPorIdException(UUID id) {
        super("Cardapio %s nao encontrado".formatted(id));
    }
}
