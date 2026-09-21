package br.com.frankcardoso.merenda.medicao.domain;

/**
 * Numeros que nao fecham entre si. E erro de quem lancou, nao falha do servidor: quem mede
 * pode somar errado, e a resposta precisa dizer qual invariante quebrou.
 */
public class MedicaoSobraInconsistenteException extends RuntimeException {
    public MedicaoSobraInconsistenteException(String mensagem) {
        super(mensagem);
    }
}
