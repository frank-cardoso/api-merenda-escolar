package br.com.frankcardoso.merenda.relatorio.application;

import java.util.UUID;

public class RelatorioNaoEncontradoException extends RuntimeException {

    public RelatorioNaoEncontradoException(UUID id) {
        super("Relatorio de IA nao encontrado: " + id);
    }
}
