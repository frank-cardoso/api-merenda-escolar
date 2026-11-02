package br.com.frankcardoso.merenda.shared.web;

public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String recurso, Object id) {
        super("%s nao encontrado: %s".formatted(recurso, id));
    }
}
