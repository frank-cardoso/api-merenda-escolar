package br.com.frankcardoso.merenda.turma.application;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.frankcardoso.merenda.biometria.application.port.ReconhecimentoFacialPort;
import br.com.frankcardoso.merenda.biometria.application.port.ReconhecimentoFacialPort.Candidato;
import br.com.frankcardoso.merenda.biometria.application.port.ReconhecimentoFacialPort.Decisao;
import br.com.frankcardoso.merenda.biometria.application.port.ReconhecimentoFacialPort.ResultadoIdentificacao;
import br.com.frankcardoso.merenda.biometria.domain.ReconhecimentoFacialIndisponivelException;
import br.com.frankcardoso.merenda.biometria.domain.RostoNaoDetectadoException;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Sem @Transactional de proposito: com open-in-view desligado, reproduz o acesso lazy fora da transacao. */
// application.yml de teste nao herda o de producao, entao open-in-view e fixado aqui.
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:cadastrohttp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CadastroHttpTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ReconhecimentoFacialPort reconhecimentoFacial;

    // O banco em memoria e compartilhado: rostos de outros testes seguem como candidatos, entao o padrao e "nao reconhecido".
    @BeforeEach
    void semRostoDuplicadoPorPadrao() {
        when(reconhecimentoFacial.identificar(any(), anyList())).thenReturn(
            new ResultadoIdentificacao(Decisao.NAO_RECONHECIDO, null, 0.9, null, 0.4, 0));
    }

    private String criar(String url, String json) throws Exception {
        var corpo = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.id");
    }

    private String turma(String sufixo) throws Exception {
        return criar("/api/v1/turmas", """
            {"codigo":"H-%s","descricao":"Turma %s","turno":"MANHA","quantidadeMaximaAlunos":30}""".formatted(sufixo, sufixo));
    }

    private String aluno(String sufixo, String turmaId, String inicio) throws Exception {
        return criar("/api/v1/alunos", """
            {"codigoPublico":"C-%s","matricula":"M-%s","nome":"Aluno %s","turmaId":"%s","dataInicio":"%s"}"""
            .formatted(sufixo, sufixo, sufixo, turmaId, inicio));
    }

    @Test
    void detalheETransferenciaMontamTurmaForaDaTransacao() throws Exception {
        var sufixo = UUID.randomUUID().toString().substring(0, 8);
        var a = turma("A" + sufixo);
        var b = turma("B" + sufixo);
        var aluno = aluno(sufixo, a, "2020-01-01");

        mvc.perform(get("/api/v1/alunos/" + aluno))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.turmaVigente.turmaCodigo").value("H-A" + sufixo))
            .andExpect(jsonPath("$.historicoVinculos.length()").value(1))
            .andExpect(jsonPath("$.historicoVinculos[0].dataFim").doesNotExist());

        mvc.perform(post("/api/v1/alunos/" + aluno + "/transferencias").contentType(MediaType.APPLICATION_JSON)
                .content("{\"turmaId\":\"" + b + "\",\"dataMudanca\":\"2020-06-01\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.turmaVigente.turmaCodigo").value("H-B" + sufixo))
            .andExpect(jsonPath("$.historicoVinculos.length()").value(2))
            .andExpect(jsonPath("$.historicoVinculos[1].dataFim").value("2020-05-31"));

        mvc.perform(post("/api/v1/alunos/" + aluno + "/transferencias").contentType(MediaType.APPLICATION_JSON)
                .content("{\"turmaId\":\"" + a + "\",\"dataMudanca\":\"2020-06-01\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void cadastrarRostoGuardaSoOEmbeddingENaoAFoto() throws Exception {
        var sufixo = UUID.randomUUID().toString().substring(0, 8);
        var t = turma("R" + sufixo);
        var aluno = aluno(sufixo, t, "2020-01-01");
        cadastrarRosto(aluno("o" + sufixo, t, "2020-01-01"), "foto-outro-" + sufixo, 0.0f, 1.0f);
        when(reconhecimentoFacial.gerarEmbedding("foto-base64-fake")).thenReturn(List.of(0.1f, 0.2f, 0.3f));

        mvc.perform(post("/api/v1/alunos/" + aluno + "/rosto").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fotoBase64\":\"foto-base64-fake\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.temRostoCadastrado").value(true));

        mvc.perform(get("/api/v1/alunos/" + aluno))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.temRostoCadastrado").value(true));
    }

    private void cadastrarRosto(String alunoId, String foto, float... vetor) throws Exception {
        var embedding = new ArrayList<Float>();
        for (float v : vetor) embedding.add(v);
        when(reconhecimentoFacial.gerarEmbedding(foto)).thenReturn(embedding);
        mvc.perform(post("/api/v1/alunos/" + alunoId + "/rosto").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fotoBase64\":\"" + foto + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void cadastrarRostoSemRostoDetectavelDevolve400() throws Exception {
        var sufixo = UUID.randomUUID().toString().substring(0, 8);
        var t = turma("S" + sufixo);
        var outro = aluno("o" + sufixo, t, "2020-01-01");
        var alvo = aluno("a" + sufixo, t, "2020-01-01");
        cadastrarRosto(outro, "foto-outro-" + sufixo, 1.0f, 0.0f);
        when(reconhecimentoFacial.identificar(eq("foto-ruim-" + sufixo), anyList()))
            .thenThrow(new RostoNaoDetectadoException("Face could not be detected."));

        mvc.perform(post("/api/v1/alunos/" + alvo + "/rosto").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fotoBase64\":\"foto-ruim-" + sufixo + "\"}"))
            .andExpect(status().isBadRequest());

        verify(reconhecimentoFacial, never()).gerarEmbedding("foto-ruim-" + sufixo);
        mvc.perform(get("/api/v1/alunos/" + alvo))
            .andExpect(jsonPath("$.temRostoCadastrado").value(false));
    }

    @Test
    void cadastrarRostoSemRostoDetectavelNaGeracaoDoEmbeddingDevolve400() throws Exception {
        var sufixo = UUID.randomUUID().toString().substring(0, 8);
        var t = turma("E" + sufixo);
        var alvo = aluno(sufixo, t, "2020-01-01");
        when(reconhecimentoFacial.gerarEmbedding(any()))
            .thenThrow(new RostoNaoDetectadoException("Face could not be detected."));

        mvc.perform(post("/api/v1/alunos/" + alvo + "/rosto").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fotoBase64\":\"foto-ruim\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/alunos/" + alvo))
            .andExpect(jsonPath("$.temRostoCadastrado").value(false));
    }

    @Test
    void cadastrarRostoJaCadastradoEmOutroAlunoDevolve409() throws Exception {
        var sufixo = UUID.randomUUID().toString().substring(0, 8);
        var t = turma("D" + sufixo);
        var outro = aluno("o" + sufixo, t, "2020-01-01");
        var alvo = aluno("a" + sufixo, t, "2020-01-01");
        cadastrarRosto(outro, "foto-outro-" + sufixo, 1.0f, 0.0f);
        when(reconhecimentoFacial.identificar(eq("foto-dup-" + sufixo), anyList())).thenReturn(
            new ResultadoIdentificacao(Decisao.RECONHECIDO, UUID.fromString(outro), 0.05, 0.8, 0.4, 0));

        mvc.perform(post("/api/v1/alunos/" + alvo + "/rosto").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fotoBase64\":\"foto-dup-" + sufixo + "\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value(
                "Este rosto já está cadastrado para o aluno C-o" + sufixo + " (Aluno o" + sufixo + ")."));

        verify(reconhecimentoFacial, never()).gerarEmbedding("foto-dup-" + sufixo);
        mvc.perform(get("/api/v1/alunos/" + alvo))
            .andExpect(jsonPath("$.temRostoCadastrado").value(false));
    }

    @Test
    void cadastrarRostoAmbiguoComOutroAlunoTambemDevolve409() throws Exception {
        var sufixo = UUID.randomUUID().toString().substring(0, 8);
        var t = turma("G" + sufixo);
        var outro = aluno("o" + sufixo, t, "2020-01-01");
        var alvo = aluno("a" + sufixo, t, "2020-01-01");
        cadastrarRosto(outro, "foto-outro-" + sufixo, 1.0f, 0.0f);
        when(reconhecimentoFacial.identificar(eq("foto-amb-" + sufixo), anyList())).thenReturn(
            new ResultadoIdentificacao(Decisao.AMBIGUO, UUID.fromString(outro), 0.10, 0.12, 0.4, 0));

        mvc.perform(post("/api/v1/alunos/" + alvo + "/rosto").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fotoBase64\":\"foto-amb-" + sufixo + "\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value(containsString("C-o" + sufixo)));

        verify(reconhecimentoFacial, never()).gerarEmbedding("foto-amb-" + sufixo);
        mvc.perform(get("/api/v1/alunos/" + alvo))
            .andExpect(jsonPath("$.temRostoCadastrado").value(false));
    }

    @Test
    void recadastrarOProprioRostoContinuaPermitido() throws Exception {
        var sufixo = UUID.randomUUID().toString().substring(0, 8);
        var t = turma("Q" + sufixo);
        var aluno = aluno(sufixo, t, "2020-01-01");
        var outro = aluno("o" + sufixo, t, "2020-01-01");
        cadastrarRosto(outro, "foto-outro-" + sufixo, 0.0f, 1.0f);
        cadastrarRosto(aluno, "foto-1-" + sufixo, 1.0f, 0.0f);
        when(reconhecimentoFacial.gerarEmbedding("foto-2-" + sufixo)).thenReturn(List.of(0.9f, 0.1f));

        mvc.perform(post("/api/v1/alunos/" + aluno + "/rosto").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fotoBase64\":\"foto-2-" + sufixo + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.temRostoCadastrado").value(true));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Candidato>> candidatos = ArgumentCaptor.forClass(List.class);
        verify(reconhecimentoFacial).identificar(eq("foto-2-" + sufixo), candidatos.capture());
        assertThat(candidatos.getValue()).isNotEmpty();
        assertThat(candidatos.getValue()).extracting(Candidato::alunoId)
            .contains(UUID.fromString(outro)).doesNotContain(UUID.fromString(aluno));
    }

    @Test
    void cadastrarRostoComServicoForaDoArDevolve503() throws Exception {
        var sufixo = UUID.randomUUID().toString().substring(0, 8);
        var t = turma("F" + sufixo);
        var outro = aluno("o" + sufixo, t, "2020-01-01");
        var alvo = aluno("a" + sufixo, t, "2020-01-01");
        cadastrarRosto(outro, "foto-outro-" + sufixo, 1.0f, 0.0f);
        when(reconhecimentoFacial.identificar(eq("foto-off-" + sufixo), anyList()))
            .thenThrow(new ReconhecimentoFacialIndisponivelException("fora do ar", null));

        mvc.perform(post("/api/v1/alunos/" + alvo + "/rosto").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fotoBase64\":\"foto-off-" + sufixo + "\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.title").value("Servico indisponivel"))
            .andExpect(jsonPath("$.detail").value(
                "Reconhecimento facial indisponível no momento. Tente novamente em instantes."));
    }

    @Test
    void excluirTurmaEmUsoDevolve409() throws Exception {
        var sufixo = UUID.randomUUID().toString().substring(0, 8);
        var t = turma("U" + sufixo);
        aluno(sufixo, t, "2020-01-01");

        mvc.perform(delete("/api/v1/turmas/" + t)).andExpect(status().isConflict());
        var livre = turma("L" + sufixo);
        mvc.perform(delete("/api/v1/turmas/" + livre)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/turmas/" + livre)).andExpect(status().isNotFound());
    }

    @Test
    void putSemAtivoNaoReativaAluno() throws Exception {
        var sufixo = UUID.randomUUID().toString().substring(0, 8);
        var t = turma("P" + sufixo);
        var id = aluno(sufixo, t, "2020-01-01");

        mvc.perform(put("/api/v1/alunos/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"matricula\":\"M-" + sufixo + "\",\"nome\":\"X\",\"ativo\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        mvc.perform(put("/api/v1/alunos/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"matricula\":\"M-" + sufixo + "\",\"nome\":\"Y\"}"))
            .andExpect(status().isBadRequest());
        var corpo = mvc.perform(get("/api/v1/alunos/" + id)).andReturn().getResponse().getContentAsString();
        assertThat((Boolean) JsonPath.read(corpo, "$.ativo")).isFalse();
    }
}
