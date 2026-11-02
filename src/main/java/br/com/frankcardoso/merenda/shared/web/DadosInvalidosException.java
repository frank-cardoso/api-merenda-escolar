package br.com.frankcardoso.merenda.shared.web;

public class DadosInvalidosException extends IllegalArgumentException {

    public DadosInvalidosException(String mensagem) {
        super(mensagem);
    }
}
