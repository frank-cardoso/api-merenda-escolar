package br.com.frankcardoso.merenda.cardapio.application;

import java.time.LocalDate;

public class CardapioDuplicadoException extends RuntimeException {

    public CardapioDuplicadoException(LocalDate data, String refeicao) {
        super("Já existe um cardápio ativo para %s em %s.".formatted(data, refeicao));
    }
}
