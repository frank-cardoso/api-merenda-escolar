package br.com.frankcardoso.merenda.cardapio.application;

import java.time.LocalDate;

public class CardapioDataPassadaException extends RuntimeException {

    public CardapioDataPassadaException(LocalDate data) {
        super("Nao e possivel cadastrar cardapio para a data passada %s".formatted(data));
    }
}
