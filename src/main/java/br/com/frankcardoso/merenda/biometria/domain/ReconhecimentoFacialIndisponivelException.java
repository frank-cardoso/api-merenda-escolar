package br.com.frankcardoso.merenda.biometria.domain;

/**
 * O reconhecimento facial nao produziu uma resposta utilizavel: servico fora do ar ou timeout, 5xx, erro de
 * cliente que nao seja "sem rosto"/"varios rostos", corpo malformado ou sem decisao, ou candidato devolvido
 * que nao foi enviado. Nao diz nada sobre a foto.
 */
public class ReconhecimentoFacialIndisponivelException extends RuntimeException {
    public ReconhecimentoFacialIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}
