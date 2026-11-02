package br.com.frankcardoso.merenda.biometria.infrastructure.python;

import br.com.frankcardoso.merenda.biometria.application.port.ReconhecimentoFacialPort;
import br.com.frankcardoso.merenda.biometria.domain.MultiplosRostosDetectadosException;
import br.com.frankcardoso.merenda.biometria.domain.RostoNaoDetectadoException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Fala com os dois endpoints de reconhecimento facial do servico Python (DeepFace).
 *
 * A foto passa por aqui so de ida: nunca fica em memoria depois do request, nem no Java nem
 * no Python. O timeout e maior que o dos outros adapters porque o primeiro request de cada
 * subida do servico Python baixa o peso do modelo (~90MB).
 */
@Component
public class PythonReconhecimentoFacialAdapter implements ReconhecimentoFacialPort {

    private static final String EMBEDDINGS_PATH = "/api/v1/reconhecimento-facial/embeddings";
    private static final String VERIFICACOES_PATH = "/api/v1/reconhecimento-facial/verificacoes";
    private static final String MODELO_PADRAO = "Facenet";

    private final RestClient restClient;

    @Autowired
    public PythonReconhecimentoFacialAdapter(
        RestClient.Builder builder,
        @Value("${merenda.analytics.base-url:http://localhost:8000}") String baseUrl,
        @Value("${merenda.reconhecimento.timeout:10s}") Duration timeout
    ) {
        this(builder
            .baseUrl(baseUrl)
            .requestFactory(requestFactory(timeout))
            .build());
    }

    PythonReconhecimentoFacialAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<Float> gerarEmbedding(String fotoBase64) {
        try {
            var resposta = restClient.post()
                .uri(EMBEDDINGS_PATH)
                .body(new EmbeddingRequest(fotoBase64, MODELO_PADRAO))
                .retrieve()
                .body(EmbeddingResponse.class);
            return Objects.requireNonNull(resposta, "Servico de reconhecimento facial retornou vazio")
                .embedding();
        } catch (HttpClientErrorException.UnprocessableEntity exception) {
            throw traduzirErro(exception);
        }
    }

    @Override
    public ResultadoVerificacaoFacial verificar(String fotoBase64, List<Float> embeddingCadastrado) {
        try {
            var resposta = restClient.post()
                .uri(VERIFICACOES_PATH)
                .body(new VerificacaoRequest(fotoBase64, embeddingCadastrado, MODELO_PADRAO))
                .retrieve()
                .body(VerificacaoResponse.class);
            var corpo = Objects.requireNonNull(resposta, "Servico de reconhecimento facial retornou vazio");
            return new ResultadoVerificacaoFacial(corpo.confere(), corpo.distancia(), corpo.limiar(), corpo.modelo());
        } catch (HttpClientErrorException.UnprocessableEntity exception) {
            throw traduzirErro(exception);
        }
    }

    /** O Python so distingue os dois casos pelo texto; "rostos detectados" so aparece na mensagem de multiplos. */
    private RuntimeException traduzirErro(HttpClientErrorException.UnprocessableEntity exception) {
        String detalhe = exception.getResponseBodyAs(ErroPython.class) != null
            ? exception.getResponseBodyAs(ErroPython.class).detail()
            : exception.getMessage();
        if (detalhe != null && detalhe.contains("rostos detectados")) {
            return new MultiplosRostosDetectadosException(detalhe);
        }
        return new RostoNaoDetectadoException(detalhe);
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }

    private record EmbeddingRequest(String fotoBase64, String modelo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingResponse(List<Float> embedding, String modelo) {
    }

    private record VerificacaoRequest(String fotoBase64, List<Float> embeddingCadastrado, String modelo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VerificacaoResponse(boolean confere, double distancia, double limiar, String modelo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErroPython(String detail) {
    }
}
