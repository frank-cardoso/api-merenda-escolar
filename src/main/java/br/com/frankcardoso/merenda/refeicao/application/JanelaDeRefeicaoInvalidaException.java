package br.com.frankcardoso.merenda.refeicao.application;

public class JanelaDeRefeicaoInvalidaException extends RuntimeException {

    public JanelaDeRefeicaoInvalidaException() {
        super("O início precisa ser antes do fim.");
    }
}
