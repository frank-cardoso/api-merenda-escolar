package br.com.frankcardoso.merenda.cardapio.application;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.time.LocalDate;

public class CardapioDuplicadoException extends RuntimeException {

    public CardapioDuplicadoException(LocalDate data, Turno turno) {
        super("Ja existe um cardapio ativo para %s no turno %s".formatted(data, turno));
    }
}
