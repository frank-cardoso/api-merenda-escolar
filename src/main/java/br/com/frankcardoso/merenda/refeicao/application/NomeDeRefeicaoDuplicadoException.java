package br.com.frankcardoso.merenda.refeicao.application;

public class NomeDeRefeicaoDuplicadoException extends RuntimeException {

    public NomeDeRefeicaoDuplicadoException(String nome) {
        super("Já existe uma refeição chamada %s na grade.".formatted(nome));
    }
}
