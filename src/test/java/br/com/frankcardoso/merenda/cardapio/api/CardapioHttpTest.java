package br.com.frankcardoso.merenda.cardapio.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:cardapiohttp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CardapioHttpTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void recusaCardapioDeRefeicaoForaDaGradeComNotFound() throws Exception {
        var desconhecida = UUID.randomUUID();
        var corpo = """
            {"data":"%s","tipoRefeicaoId":"%s","nomeRefeicao":"Arroz","descricao":null,
             "itens":[{"nome":"arroz","quantidade":"10 kg"}],"quantidadePlanejada":100}
            """.formatted(LocalDate.now().plusDays(1), desconhecida);

        mvc.perform(post("/api/v1/cardapios").contentType(MediaType.APPLICATION_JSON).content(corpo))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Refeição %s não existe na grade.".formatted(desconhecida)));
    }
}
