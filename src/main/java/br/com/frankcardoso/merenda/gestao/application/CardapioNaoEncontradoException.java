package br.com.frankcardoso.merenda.gestao.application;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.time.LocalDate;

public class CardapioNaoEncontradoException extends RuntimeException {

    public CardapioNaoEncontradoException(LocalDate data, Turno turno) {
        super("Nao existe cardapio ativo para %s no turno %s".formatted(data, turno));
    }
}
