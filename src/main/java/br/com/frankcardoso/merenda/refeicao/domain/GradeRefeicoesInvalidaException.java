package br.com.frankcardoso.merenda.refeicao.domain;

public class GradeRefeicoesInvalidaException extends RuntimeException {

    public GradeRefeicoesInvalidaException(String mensagem) {
        super(mensagem);
    }
}
