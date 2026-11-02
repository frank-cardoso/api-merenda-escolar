package br.com.frankcardoso.merenda.turma.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
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
