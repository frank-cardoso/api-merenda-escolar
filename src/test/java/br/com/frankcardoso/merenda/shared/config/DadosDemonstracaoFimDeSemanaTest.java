package br.com.frankcardoso.merenda.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.catalogo.infrastructure.ReceitaRepository;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.medicao.infrastructure.MedicaoSobraRepository;
import br.com.frankcardoso.merenda.refeicao.application.GradeRefeicoesService;
import br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao;
import br.com.frankcardoso.merenda.turma.application.EnturmacaoService;
import br.com.frankcardoso.merenda.turma.infrastructure.EnturmacaoRepository;
import br.com.frankcardoso.merenda.turma.infrastructure.TurmaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DadosDemonstracaoFimDeSemanaTest {

    private static final LocalDate SABADO = LocalDate.of(2030, 3, 16);

    @Autowired AlunoRepository alunos;
    @Autowired CardapioRepository cardapios;
    @Autowired AuditoriaConsumoRepository auditorias;
    @Autowired MedicaoSobraRepository medicoes;
    @Autowired CatalogoItensDemonstracao catalogo;
    @Autowired ReceitaRepository receitas;
    @Autowired ObjectMapper objectMapper;
    @Autowired TurmaRepository turmas;
    @Autowired EnturmacaoRepository enturmacoes;
    @Autowired EnturmacaoService enturmacaoService;
    @Autowired GradeRefeicoesService grade;
    @Autowired EntityManager em;

    @Test
    void cardapioDeHojeExisteEmTodasAsRefeicoesNoSabado() {
        assertThat(SABADO.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        auditorias.deleteAll();
        enturmacoes.deleteAll();
        medicoes.deleteAll();
        alunos.deleteAll();
        turmas.deleteAll();
        cardapios.deleteAll();
        em.flush();
        Clock relogio = Clock.fixed(
            SABADO.atTime(10, 0).atZone(ApplicationConfig.ZONA_OPERACIONAL).toInstant(),
            ApplicationConfig.ZONA_OPERACIONAL);
        new DadosDemonstracaoLoader(alunos, cardapios, auditorias, medicoes, catalogo, receitas,
            turmas, enturmacoes, enturmacaoService, grade, objectMapper, relogio, 10, 6).carregar();

        for (var refeicao : RefeicaoPadrao.values()) {
            assertThat(cardapios.findByDataAndTipoRefeicaoIdAndAtivoTrue(SABADO, refeicao.id()))
                .as(refeicao.nome()).isPresent();
        }
    }
}
