package br.com.frankcardoso.merenda.fechamento.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fechamentohttp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FechamentoHttpTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void diaSemDataRetornaBadRequest() throws Exception {
        mvc.perform(get("/api/v1/fechamentos/dia")).andExpect(status().isBadRequest());
    }

    @Test
    void diaListaAsCincoRefeicoesComHorarioHoraMinuto() throws Exception {
        mvc.perform(get("/api/v1/fechamentos/dia").param("data", "2030-03-14"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$[1].inicio").value("09:30"))
            .andExpect(jsonPath("$[0].situacao").value("SEM_CARDAPIO"));
    }

    @Test
    void pendenciasValidaOLimiteDeDias() throws Exception {
        mvc.perform(get("/api/v1/fechamentos/pendencias").param("ate", "2030-03-14").param("dias", "0"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/fechamentos/pendencias").param("ate", "2030-03-14").param("dias", "32"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/fechamentos/pendencias").param("ate", "2030-03-14"))
            .andExpect(status().isOk());
    }
}
