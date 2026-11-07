package br.com.frankcardoso.merenda.biometria.infrastructure.python;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.frankcardoso.merenda.biometria.application.port.ReconhecimentoFacialPort.Candidato;
import br.com.frankcardoso.merenda.biometria.application.port.ReconhecimentoFacialPort.Decisao;
import br.com.frankcardoso.merenda.biometria.domain.MultiplosRostosDetectadosException;
import br.com.frankcardoso.merenda.biometria.domain.ReconhecimentoFacialIndisponivelException;
import br.com.frankcardoso.merenda.biometria.domain.RostoNaoDetectadoException;
import java.util.List;
import java.util.UUID;
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
    void validacao422ComDetailEmListaViraIndisponivel() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new PythonReconhecimentoFacialAdapter(
            restClientBuilder.baseUrl("http://analytics:8000").build());

        server.expect(once(), requestTo("http://analytics:8000/api/v1/reconhecimento-facial/identificacoes"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(APPLICATION_JSON).body("""
                {"detail":[{"loc":["body","candidatos",0,"embedding"],"msg":"List should have at least 1 item","type":"too_short"}]}
                """));

        assertThatThrownBy(() -> adapter.identificar("base64-fake",
            List.of(new Candidato(UUID.randomUUID(), List.of(1.0f)))))
            .isInstanceOf(ReconhecimentoFacialIndisponivelException.class)
            .hasCauseInstanceOf(org.springframework.web.client.HttpClientErrorException.class);
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

    private static final UUID ALUNO_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ALUNO_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void identificarEnviaFotoECandidatosEMapeiaResposta() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new PythonReconhecimentoFacialAdapter(
            restClientBuilder.baseUrl("http://analytics:8000").build());

        server.expect(once(), requestTo("http://analytics:8000/api/v1/reconhecimento-facial/identificacoes"))
            .andExpect(method(POST))
            .andExpect(content().contentType(APPLICATION_JSON))
            .andExpect(content().json("""
                {"fotoBase64": "base64-fake", "modelo": "Facenet", "candidatos": [
                  {"id": "00000000-0000-0000-0000-00000000000a", "embedding": [1.0, 0.0]},
                  {"id": "00000000-0000-0000-0000-00000000000b", "embedding": [0.0, 1.0]}
                ]}
                """))
            .andRespond(withSuccess("""
                {"decisao": "RECONHECIDO", "melhorCandidatoId": "00000000-0000-0000-0000-00000000000a",
                 "distancia": 0.1, "distanciaSegundo": 0.9, "limiar": 0.4, "margemMinima": 0.05,
                 "candidatosIgnorados": 3, "modelo": "Facenet"}
                """, APPLICATION_JSON));

        var resultado = adapter.identificar("base64-fake", List.of(
            new Candidato(ALUNO_A, List.of(1.0f, 0.0f)),
            new Candidato(ALUNO_B, List.of(0.0f, 1.0f))));

        assertThat(resultado.decisao()).isEqualTo(Decisao.RECONHECIDO);
        assertThat(resultado.melhorCandidatoId()).isEqualTo(ALUNO_A);
        assertThat(resultado.distancia()).isEqualTo(0.1);
        assertThat(resultado.distanciaSegundo()).isEqualTo(0.9);
        assertThat(resultado.limiar()).isEqualTo(0.4);
        assertThat(resultado.candidatosIgnorados()).isEqualTo(3);
        server.verify();
    }

    @Test
    void identificarComRespostaSemCandidatoMapeiaNulos() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new PythonReconhecimentoFacialAdapter(
            restClientBuilder.baseUrl("http://analytics:8000").build());

        server.expect(once(), requestTo("http://analytics:8000/api/v1/reconhecimento-facial/identificacoes"))
            .andRespond(withSuccess("""
                {"decisao": "NAO_RECONHECIDO", "melhorCandidatoId": null, "distancia": null,
                 "distanciaSegundo": null, "limiar": 0.4, "margemMinima": 0.05,
                 "candidatosIgnorados": 1, "modelo": "Facenet"}
                """, APPLICATION_JSON));

        var resultado = adapter.identificar("base64-fake", List.of(new Candidato(ALUNO_A, List.of(1.0f, 0.0f))));

        assertThat(resultado.decisao()).isEqualTo(Decisao.NAO_RECONHECIDO);
        assertThat(resultado.melhorCandidatoId()).isNull();
        assertThat(resultado.distancia()).isNull();
        assertThat(resultado.distanciaSegundo()).isNull();
        assertThat(resultado.candidatosIgnorados()).isEqualTo(1);
    }

    @Test
    void identificarSemRostoLevantaRostoNaoDetectado() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new PythonReconhecimentoFacialAdapter(
            restClientBuilder.baseUrl("http://analytics:8000").build());

        server.expect(once(), requestTo("http://analytics:8000/api/v1/reconhecimento-facial/identificacoes"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(APPLICATION_JSON).body("""
                {"detail": "Face could not be detected."}
                """));

        assertThatThrownBy(() -> adapter.identificar("base64-fake",
            List.of(new Candidato(ALUNO_A, List.of(1.0f, 0.0f)))))
            .isInstanceOf(RostoNaoDetectadoException.class);
    }

    @Test
    void servicoForaDoArLevantaIndisponivel() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new PythonReconhecimentoFacialAdapter(
            restClientBuilder.baseUrl("http://analytics:8000").build());

        server.expect(times(2), requestTo(org.hamcrest.Matchers.startsWith("http://analytics:8000")))
            .andRespond(withException(new java.net.ConnectException("recusada")));

        assertThatThrownBy(() -> adapter.gerarEmbedding("base64-fake"))
            .isInstanceOf(ReconhecimentoFacialIndisponivelException.class);
        assertThatThrownBy(() -> adapter.identificar("base64-fake",
            List.of(new Candidato(ALUNO_A, List.of(1.0f, 0.0f)))))
            .isInstanceOf(ReconhecimentoFacialIndisponivelException.class);
    }

    @Test
    void identificarCom5xxLevantaIndisponivel() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new PythonReconhecimentoFacialAdapter(
            restClientBuilder.baseUrl("http://analytics:8000").build());

        server.expect(times(2), requestTo(org.hamcrest.Matchers.startsWith("http://analytics:8000")))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> adapter.identificar("base64-fake",
            List.of(new Candidato(ALUNO_A, List.of(1.0f, 0.0f)))))
            .isInstanceOf(ReconhecimentoFacialIndisponivelException.class)
            .hasCauseInstanceOf(org.springframework.web.client.HttpServerErrorException.class);
        assertThatThrownBy(() -> adapter.gerarEmbedding("base64-fake"))
            .isInstanceOf(ReconhecimentoFacialIndisponivelException.class);
    }

    @Test
    void identificarComCorpoMalformadoLevantaIndisponivel() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new PythonReconhecimentoFacialAdapter(
            restClientBuilder.baseUrl("http://analytics:8000").build());

        server.expect(once(), requestTo("http://analytics:8000/api/v1/reconhecimento-facial/identificacoes"))
            .andRespond(withSuccess("""
                {"decisao": "TALVEZ", "limiar": 0.4, "candidatosIgnorados": 0}
                """, APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.identificar("base64-fake",
            List.of(new Candidato(ALUNO_A, List.of(1.0f, 0.0f)))))
            .isInstanceOf(ReconhecimentoFacialIndisponivelException.class)
            .hasCauseInstanceOf(org.springframework.web.client.RestClientException.class);
    }

    @Test
    void identificarSemDecisaoLevantaIndisponivel() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new PythonReconhecimentoFacialAdapter(
            restClientBuilder.baseUrl("http://analytics:8000").build());

        server.expect(once(), requestTo("http://analytics:8000/api/v1/reconhecimento-facial/identificacoes"))
            .andRespond(withSuccess("""
                {"decisao": null, "limiar": 0.4, "candidatosIgnorados": 0}
                """, APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.identificar("base64-fake",
            List.of(new Candidato(ALUNO_A, List.of(1.0f, 0.0f)))))
            .isInstanceOf(ReconhecimentoFacialIndisponivelException.class);
    }
}
