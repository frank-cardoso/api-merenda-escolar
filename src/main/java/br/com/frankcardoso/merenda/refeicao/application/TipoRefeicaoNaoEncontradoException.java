package br.com.frankcardoso.merenda.refeicao.application;

import java.util.UUID;

public class TipoRefeicaoNaoEncontradoException extends RuntimeException {

    public TipoRefeicaoNaoEncontradoException(UUID id) {
        super("Refeição %s não existe na grade.".formatted(id));
    }
}
