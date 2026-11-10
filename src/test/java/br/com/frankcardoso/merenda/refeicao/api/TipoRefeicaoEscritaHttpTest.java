package br.com.frankcardoso.merenda.refeicao.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:gradeescrita;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TipoRefeicaoEscritaHttpTest {

    private static final String URL = "/api/v1/tipos-refeicao";

    @Autowired
    private MockMvc mvc;

    private static String corpo(String nome, String inicio, String fim, String turnos) {
        return """
            {"nome": "%s", "inicio": "%s", "fim": "%s", "turnos": %s}""".formatted(nome, inicio, fim, turnos);
    }

    @Test
    void criaRefeicaoERenumeraPeloInicio() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content(corpo("Lanche extra", "14:00", "14:30", "[\"TARDE\"]")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nome").value("Lanche extra"))
            .andExpect(jsonPath("$.ordem").value(4))
            .andExpect(jsonPath("$.turnos[0]").value("TARDE"));

        mvc.perform(get(URL))
            .andExpect(jsonPath("$.length()").value(6))
            .andExpect(jsonPath("$[3].nome").value("Lanche extra"))
            .andExpect(jsonPath("$[4].nome").value("Lanche da tarde"))
            .andExpect(jsonPath("$[4].ordem").value(5))
            .andExpect(jsonPath("$[5].nome").value("Lanche da noite"))
            .andExpect(jsonPath("$[5].ordem").value(6));
    }

    @Test
    void editaJanelaSemAcusarSobreposicaoConsigoMesma() throws Exception {
        mvc.perform(put(URL + "/" + RefeicaoPadrao.ALMOCO.id()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo("Almoço", "11:45", "13:15", "[\"INTEGRAL\"]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inicio").value("11:45:00"))
            .andExpect(jsonPath("$.fim").value("13:15:00"))
            .andExpect(jsonPath("$.ordem").value(3));
    }

    @Test
    void editaAOrdemQuandoAJanelaPassaDeOutra() throws Exception {
        mvc.perform(put(URL + "/" + RefeicaoPadrao.LANCHE_DA_NOITE.id()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo("Lanche da noite", "08:00", "09:00", "[\"NOITE\"]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ordem").value(2));

        mvc.perform(get(URL))
            .andExpect(jsonPath("$[1].nome").value("Lanche da noite"))
            .andExpect(jsonPath("$[2].nome").value("Lanche da manhã"))
            .andExpect(jsonPath("$[4].nome").value("Lanche da tarde"));
    }

    @Test
    void sobreposicaoParaOMesmoTurnoRetorna422ENaoGrava() throws Exception {
        mvc.perform(put(URL + "/" + RefeicaoPadrao.ALMOCO.id()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo("Almoço", "07:30", "08:30", "[\"INTEGRAL\"]")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value(containsString("se sobrepõem para o mesmo turno")));

        mvc.perform(get(URL))
            .andExpect(jsonPath("$[2].nome").value("Almoço"))
            .andExpect(jsonPath("$[2].inicio").value("11:30:00"))
            .andExpect(jsonPath("$[2].fim").value("13:00:00"))
            .andExpect(jsonPath("$[2].ordem").value(3));
    }

    @Test
    void sobreposicaoNaCriacaoRetorna422ENaoGrava() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content(corpo("Almoço tardio", "12:00", "13:30", "[\"INTEGRAL\"]")))
            .andExpect(status().isUnprocessableEntity());

        mvc.perform(get(URL)).andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void inicioDepoisDoFimRetorna422() throws Exception {
        mvc.perform(put(URL + "/" + RefeicaoPadrao.ALMOCO.id()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo("Almoço", "13:00", "11:30", "[\"INTEGRAL\"]")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("O início precisa ser antes do fim."));
    }

    @Test
    void inicioIgualAoFimRetorna422() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content(corpo("Instante", "14:00", "14:00", "[]")))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void nomeRepetidoSemDiferenciarMaiusculasRetorna409() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content(corpo("almoço", "14:00", "14:30", "[\"TARDE\"]")))
            .andExpect(status().isConflict());
    }

    @Test
    void manterOProprioNomeNaEdicaoNaoEhConflito() throws Exception {
        mvc.perform(put(URL + "/" + RefeicaoPadrao.ALMOCO.id()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo("ALMOÇO", "11:30", "13:00", "[\"INTEGRAL\"]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("ALMOÇO"));
    }

    @Test
    void nomeDeOutraRefeicaoNaEdicaoRetorna409() throws Exception {
        mvc.perform(put(URL + "/" + RefeicaoPadrao.ALMOCO.id()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo("Café da manhã", "11:30", "13:00", "[\"INTEGRAL\"]")))
            .andExpect(status().isConflict());
    }

    @Test
    void idInexistenteRetorna404() throws Exception {
        mvc.perform(put(URL + "/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo("Almoço", "11:30", "13:00", "[\"INTEGRAL\"]")))
            .andExpect(status().isNotFound());
    }

    @Test
    void turnosVaziosSaoAceitos() throws Exception {
        mvc.perform(put(URL + "/" + RefeicaoPadrao.LANCHE_DA_NOITE.id()).contentType(MediaType.APPLICATION_JSON)
                .content(corpo("Lanche da noite", "20:00", "21:00", "[]")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.turnos.length()").value(0));
    }

    @Test
    void corpoInvalidoRetorna400() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\": \" \", \"inicio\": \"14:00\", \"fim\": \"14:30\", \"turnos\": []}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\": \"X\", \"inicio\": \"14:00\", \"fim\": \"14:30\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content(corpo("X", "14:00", "14:30", "[null]")))
            .andExpect(status().isBadRequest());
    }
}
