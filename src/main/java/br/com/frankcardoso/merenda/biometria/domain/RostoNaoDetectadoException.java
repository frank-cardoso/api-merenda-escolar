package br.com.frankcardoso.merenda.biometria.domain;

/** A foto recebida nao tem rosto detectavel. Nao e "nao confere", e falta de dado. */
public class RostoNaoDetectadoException extends RuntimeException {
    public RostoNaoDetectadoException(String message) {
        super(message);
    }
}
