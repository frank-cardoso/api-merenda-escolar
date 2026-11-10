package br.com.frankcardoso.merenda.refeicao.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.frankcardoso.merenda.refeicao.application.GradeRefeicoesService;
import br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao;
import br.com.frankcardoso.merenda.refeicao.domain.TipoRefeicao;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:gradesemeada;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GradeSemeadaHttpTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private GradeRefeicoesService service;

    @Test
    void gradeSemeadaEhValidaEUsaOsIdsDoEnum() {
        service.grade();

        List<TipoRefeicao> grade = service.listar();
        RefeicaoPadrao[] padroes = RefeicaoPadrao.values();
        assertThat(grade).hasSize(padroes.length);
        assertThat(grade.stream().map(TipoRefeicao::getId).toList())
            .isEqualTo(Arrays.stream(padroes).map(RefeicaoPadrao::id).toList());
        assertThat(grade.stream().map(TipoRefeicao::getNome).toList())
            .isEqualTo(Arrays.stream(padroes).map(RefeicaoPadrao::nome).toList());
    }

    @Test
    void listaAGradeEmOrdem() throws Exception {
        mvc.perform(get("/api/v1/tipos-refeicao"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$[2].nome").value("Almoço"))
            .andExpect(jsonPath("$[2].inicio").value("11:30:00"))
            .andExpect(jsonPath("$[2].fim").value("13:00:00"))
            .andExpect(jsonPath("$[2].turnos[0]").value("INTEGRAL"))
            .andExpect(jsonPath("$[2].turnos.length()").value(1))
            .andExpect(jsonPath("$[1].turnos[0]").value("MANHA"))
            .andExpect(jsonPath("$[1].turnos[1]").value("INTEGRAL"));
    }
}
