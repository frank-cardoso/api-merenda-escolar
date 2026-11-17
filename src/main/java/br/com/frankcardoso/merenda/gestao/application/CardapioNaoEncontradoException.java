package br.com.frankcardoso.merenda.gestao.application;

import java.time.LocalDate;

public class CardapioNaoEncontradoException extends RuntimeException {

    public CardapioNaoEncontradoException(LocalDate data, String refeicao) {
        super("Não existe cardápio ativo para %s em %s.".formatted(refeicao, data));
    }
}
