package br.com.frankcardoso.merenda.cardapio.application;

/**
 * Item do cardapio que nao existe no catalogo de receitas.
 *
 * Recusar e deliberado: era aqui que entrava nome digitado a mao, e dele vinha o casamento
 * aproximado com o historico que produziu correlacao sobre nada (item 3 da RETROSPECTIVA).
 */
public class ReceitaDesconhecidaException extends RuntimeException {
    public ReceitaDesconhecidaException(String mensagem) {
        super(mensagem);
    }
}
