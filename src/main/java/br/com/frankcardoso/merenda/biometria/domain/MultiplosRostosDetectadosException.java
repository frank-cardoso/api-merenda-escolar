package br.com.frankcardoso.merenda.biometria.domain;

/** Cadastro exige uma foto com um rosto so; com mais de um, nao da pra saber qual vale. */
public class MultiplosRostosDetectadosException extends RuntimeException {
    public MultiplosRostosDetectadosException(String message) {
        super(message);
    }
}
