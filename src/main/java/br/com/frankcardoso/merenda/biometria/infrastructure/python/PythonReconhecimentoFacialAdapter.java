package br.com.frankcardoso.merenda.biometria.infrastructure.python;

import br.com.frankcardoso.merenda.biometria.application.port.ReconhecimentoFacialPort;
import br.com.frankcardoso.merenda.biometria.domain.MultiplosRostosDetectadosException;
import br.com.frankcardoso.merenda.biometria.domain.ReconhecimentoFacialIndisponivelException;
import br.com.frankcardoso.merenda.biometria.domain.RostoNaoDetectadoException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

/**
 * Fala com os endpoints de reconhecimento facial do servico Python (DeepFace).
 *
 * A foto passa por aqui so de ida: nunca fica em memoria depois do request, nem no Java nem
 * no Python. O timeout e maior que o dos outros adapters porque o primeiro request de cada
 * subida do servico Python baixa o peso do modelo (~90MB).
 */
@Component
public class PythonReconhecimentoFacialAdapter implements ReconhecimentoFacialPort {

    private static final String EMBEDDINGS_PATH = "/api/v1/reconhecimento-facial/embeddings";
    private static final String IDENTIFICACOES_PATH = "/api/v1/reconhecimento-facial/identificacoes";
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
            if (resposta == null || resposta.embedding() == null) {
                throw new ReconhecimentoFacialIndisponivelException(
                    "Servico de reconhecimento facial retornou resposta sem embedding", null);
            }
            return resposta.embedding();
        } catch (HttpClientErrorException.UnprocessableEntity exception) {
            throw traduzirErro(exception);
        } catch (RestClientException exception) {
            throw indisponivel(exception);
        }
    }

    @Override
    public ResultadoIdentificacao identificar(String fotoBase64, List<Candidato> candidatos) {
        try {
            var resposta = restClient.post()
                .uri(IDENTIFICACOES_PATH)
                .body(new IdentificacaoRequest(
                    fotoBase64,
                    candidatos.stream()
                        .map(candidato -> new CandidatoRequest(candidato.alunoId().toString(), candidato.embedding()))
                        .toList(),
                    MODELO_PADRAO))
                .retrieve()
                .body(IdentificacaoResponse.class);
            if (resposta == null || resposta.decisao() == null) {
                throw new ReconhecimentoFacialIndisponivelException(
                    "Servico de reconhecimento facial retornou resposta sem decisao", null);
            }
            return new ResultadoIdentificacao(resposta.decisao(), resposta.melhorCandidatoId(),
                resposta.distancia(), resposta.distanciaSegundo(), resposta.limiar(),
                resposta.candidatosIgnorados());
        } catch (HttpClientErrorException.UnprocessableEntity exception) {
            throw traduzirErro(exception);
        } catch (RestClientException exception) {
            throw indisponivel(exception);
        }
    }

    private ReconhecimentoFacialIndisponivelException indisponivel(RuntimeException exception) {
        return new ReconhecimentoFacialIndisponivelException(
            "Servico de reconhecimento facial indisponivel", exception);
    }

    /**
     * O Python so distingue os dois casos de rosto pelo texto; "rostos detectados" so aparece na mensagem de
     * multiplos. Um 422 de validacao (detail em lista) ou sem texto reconhecivel nao e problema da foto.
     */
    private RuntimeException traduzirErro(HttpClientErrorException.UnprocessableEntity exception) {
        String detalhe = null;
        try {
            ErroPython corpo = exception.getResponseBodyAs(ErroPython.class);
            if (corpo != null && corpo.detail() instanceof String texto) detalhe = texto;
        } catch (RuntimeException ignorada) {
            // corpo fora do formato esperado: cai para a mensagem da excecao
        }
        if (detalhe == null) detalhe = exception.getMessage();
        if (detalhe.contains("rostos detectados")) return new MultiplosRostosDetectadosException(detalhe);
        if (detalhe.contains("Face could not be detected")) {
            return new RostoNaoDetectadoException(detalhe);
        }
        return new ReconhecimentoFacialIndisponivelException(
            "Servico de reconhecimento facial recusou a requisicao: " + detalhe, exception);
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

    private record IdentificacaoRequest(String fotoBase64, List<CandidatoRequest> candidatos, String modelo) {
    }

    private record CandidatoRequest(String id, List<Float> embedding) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IdentificacaoResponse(Decisao decisao, UUID melhorCandidatoId, Double distancia,
                                         Double distanciaSegundo, double limiar, int candidatosIgnorados) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErroPython(Object detail) {
    }
}
