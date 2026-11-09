package br.com.frankcardoso.merenda.relatorio.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:relatoriohttp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RelatorioIAHttpTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void amostraInsuficienteRetorna422ComQuantidadeDeDiasNoDetalhe() throws Exception {
        // Data de 2001: nenhum teste grava fechamento nela, então a contagem é sempre zero.
        mvc.perform(post("/api/v1/relatorios-ia").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"dataReferencia":"2001-03-14","turno":"MANHA"}"""))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.title").value("Amostra insuficiente"))
            .andExpect(jsonPath("$.detail").value(
                "O relatório de IA precisa de pelo menos 20 dias com fechamento nos últimos 30 dias deste turno; há 0."));
    }
}
