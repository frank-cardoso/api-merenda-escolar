package br.com.frankcardoso.merenda.gestao.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:gestaohttp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GestaoHttpTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void consolidacaoDeRefeicaoForaDaGradeRetornaNotFound() throws Exception {
        var desconhecida = UUID.randomUUID();

        mvc.perform(get("/api/v1/gestao/consolidacoes")
                .param("data", "2030-03-14").param("tipoRefeicaoId", desconhecida.toString()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Refeição %s não existe na grade.".formatted(desconhecida)));
    }

    @Test
    void cardapiosParaAnaliseDeRefeicaoForaDaGradeRetornaNotFound() throws Exception {
        var desconhecida = UUID.randomUUID();

        mvc.perform(get("/api/v1/gestao/cardapios-analise")
                .param("inicio", "2030-03-01").param("fim", "2030-03-14")
                .param("tipoRefeicaoId", desconhecida.toString()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Refeição %s não existe na grade.".formatted(desconhecida)));
    }
}
