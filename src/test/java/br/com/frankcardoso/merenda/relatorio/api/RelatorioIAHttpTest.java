package br.com.frankcardoso.merenda.relatorio.api;

import static br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao.ALMOCO;
import static br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao.CAFE_DA_MANHA;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.frankcardoso.merenda.catalogo.domain.Receita;
import br.com.frankcardoso.merenda.catalogo.infrastructure.ReceitaRepository;
import br.com.frankcardoso.merenda.medicao.domain.MedicaoSobra;
import br.com.frankcardoso.merenda.medicao.infrastructure.MedicaoSobraRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
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

    @Autowired
    private MedicaoSobraRepository medicoes;

    @Autowired
    private ReceitaRepository receitas;

    @Test
    void amostraInsuficienteRetorna422ComQuantidadeDeDiasNoDetalhe() throws Exception {
        // Data de 2001: nenhum teste grava fechamento nela, então a contagem é sempre zero.
        mvc.perform(post("/api/v1/relatorios-ia").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"dataReferencia":"2001-03-14","tipoRefeicaoId":"%s"}""".formatted(ALMOCO.id())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.title").value("Amostra insuficiente"))
            .andExpect(jsonPath("$.detail").value(
                "O relatório de IA precisa de pelo menos 20 dias com fechamento nos últimos 30 dias nesta refeição (Almoço); há 0."));
    }

    @Test
    void refeicaoForaDaGradeRetorna404() throws Exception {
        var desconhecida = UUID.randomUUID();
        mvc.perform(post("/api/v1/relatorios-ia").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"dataReferencia":"2001-03-14","tipoRefeicaoId":"%s"}""".formatted(desconhecida)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Refeição %s não existe na grade.".formatted(desconhecida)));
    }

    @Test
    void amostraContaSoDiasDaRefeicao() throws Exception {
        var receita = receitas.save(new Receita(UUID.randomUUID(), "Arroz de teste", null)).getId();
        for (int dia = 1; dia <= 20; dia++) {
            medicoes.save(new MedicaoSobra(UUID.randomUUID(), LocalDate.of(2002, 3, dia), ALMOCO.id(), 1,
                receita, 10, 8, 2, 1, "MANUAL", Instant.now()));
        }

        mvc.perform(post("/api/v1/relatorios-ia").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"dataReferencia":"2002-03-20","tipoRefeicaoId":"%s"}""".formatted(ALMOCO.id())))
            .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/relatorios-ia").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"dataReferencia":"2002-03-20","tipoRefeicaoId":"%s"}""".formatted(CAFE_DA_MANHA.id())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value(
                "O relatório de IA precisa de pelo menos 20 dias com fechamento nos últimos 30 dias nesta refeição (Café da manhã); há 0."));
    }
}
