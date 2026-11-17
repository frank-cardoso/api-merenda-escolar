package br.com.frankcardoso.merenda.shared.web;

public class ConflitoCadastroException extends IllegalStateException {

    public ConflitoCadastroException(String mensagem) {
        super(mensagem);
    }
}
