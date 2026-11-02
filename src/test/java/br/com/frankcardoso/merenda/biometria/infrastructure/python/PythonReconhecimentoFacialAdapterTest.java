package br.com.frankcardoso.merenda.biometria.infrastructure.python;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.frankcardoso.merenda.biometria.domain.MultiplosRostosDetectadosException;
import br.com.frankcardoso.merenda.biometria.domain.RostoNaoDetectadoException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

class PythonReconhecimentoFacialAdapterTest {

    @Test
    void deveGerarEmbeddingAPartirDaFoto() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new PythonReconhecimentoFacialAdapter(
            restClientBuilder.baseUrl("http://analytics:8000").build());

        server.expect(once(), requestTo("http://analytics:8000/api/v1/reconhecimento-facial/embeddings"))
            .andExpect(method(POST))
            .andExpect(content().contentType(APPLICATION_JSON))
            .andExpect(content().json("""
                {"fotoBase64": "base64-fake", "modelo": "Facenet"}
                """))
            .andRespond(withSuccess("""
                {"embedding": [0.1, 0.2, 0.3], "modelo": "Facenet"}
                """, APPLICATION_JSON));

        var embedding = adapter.gerarEmbedding("base64-fake");

        assertThat(embedding).containsExactly(0.1f, 0.2f, 0.3f);
        server.verify();
    }

    @Test
    void deveLancarRostoNaoDetectadoQuandoPythonRecusaAFoto() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new PythonReconhecimentoFacialAdapter(
            restClientBuilder.baseUrl("http://analytics:8000").build());

        server.expect(once(), requestTo("http://analytics:8000/api/v1/reconhecimento-facial/embeddings"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(APPLICATION_JSON).body("""
                {"detail": "Face could not be detected."}
                """));

        assertThatThrownBy(() -> adapter.gerarEmbedding("base64-fake"))
            .isInstanceOf(RostoNaoDetectadoException.class)
            .hasMessageContaining("Face could not be detected");
    }

    @Test
    void deveLancarMultiplosRostosDetectadosQuandoFotoTemMaisDeUmRosto() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new PythonReconhecimentoFacialAdapter(
            restClientBuilder.baseUrl("http://analytics:8000").build());

        server.expect(once(), requestTo("http://analytics:8000/api/v1/reconhecimento-facial/embeddings"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(APPLICATION_JSON).body("""
                {"detail": "2 rostos detectados na foto; cadastro exige exatamente um."}
                """));

        assertThatThrownBy(() -> adapter.gerarEmbedding("base64-fake"))
            .isInstanceOf(MultiplosRostosDetectadosException.class);
    }

    @Test
    void deveVerificarRostoContraEmbeddingCadastrado() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new PythonReconhecimentoFacialAdapter(
            restClientBuilder.baseUrl("http://analytics:8000").build());

        server.expect(once(), requestTo("http://analytics:8000/api/v1/reconhecimento-facial/verificacoes"))
            .andExpect(method(POST))
            .andExpect(content().json("""
                {"fotoBase64": "base64-fake", "embeddingCadastrado": [0.1, 0.2, 0.3], "modelo": "Facenet"}
                """))
            .andRespond(withSuccess("""
                {"confere": true, "distancia": 0.12, "limiar": 0.4, "modelo": "Facenet"}
                """, APPLICATION_JSON));

        var resultado = adapter.verificar("base64-fake", List.of(0.1f, 0.2f, 0.3f));

        assertThat(resultado.confere()).isTrue();
        assertThat(resultado.distancia()).isEqualTo(0.12);
        assertThat(resultado.limiar()).isEqualTo(0.4);
        assertThat(resultado.modelo()).isEqualTo("Facenet");
        server.verify();
    }
}
