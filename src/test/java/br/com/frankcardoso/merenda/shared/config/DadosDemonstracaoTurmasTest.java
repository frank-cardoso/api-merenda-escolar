package br.com.frankcardoso.merenda.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.catalogo.infrastructure.ReceitaRepository;
import br.com.frankcardoso.merenda.fila.domain.AuditoriaConsumo;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.medicao.infrastructure.MedicaoSobraRepository;
import br.com.frankcardoso.merenda.turma.application.EnturmacaoService;
import br.com.frankcardoso.merenda.turma.domain.Enturmacao;
import br.com.frankcardoso.merenda.turma.infrastructure.EnturmacaoRepository;
import br.com.frankcardoso.merenda.turma.infrastructure.TurmaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DadosDemonstracaoTurmasTest {

    private static final int DIAS = 20;
    private static final int ALUNOS = 18;
    private static final LocalDate HOJE = LocalDate.of(2030, 3, 13);
    private static final LocalDate PRIMEIRO_DIA = HOJE.minusDays(DIAS);

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
    @Autowired EntityManager em;

    @BeforeEach
    void carregarDemonstracao() {
        // O banco em memoria e compartilhado entre contextos e pode trazer dados de outro teste.
        auditorias.deleteAll();
        enturmacoes.deleteAll();
        medicoes.deleteAll();
        alunos.deleteAll();
        turmas.deleteAll();
        cardapios.deleteAll();
        em.flush();
        Clock relogio = Clock.fixed(
            HOJE.atTime(10, 0).atZone(ApplicationConfig.ZONA_OPERACIONAL).toInstant(),
            ApplicationConfig.ZONA_OPERACIONAL);
        new DadosDemonstracaoLoader(alunos, cardapios, auditorias, medicoes, catalogo, receitas,
            turmas, enturmacoes, enturmacaoService, objectMapper, relogio, DIAS, ALUNOS).carregar();
    }

    @Test
    void criaNoveTurmasComEnturmacaoCobrindoTodoOHistorico() {
        assertThat(turmas.findAll()).extracting(t -> t.getCodigo())
            .containsExactlyInAnyOrder("1A", "2A", "3A", "4A", "5A", "6A", "7A", "8A", "9A");

        var todos = alunos.findAll();
        assertThat(todos).hasSize(ALUNOS);
        for (var aluno : todos) {
            var vinculos = enturmacoes.findByAlunoIdOrderByDataInicioDesc(aluno.getId());
            assertThat(vinculos).as(aluno.getCodigoPublico()).isNotEmpty();
            for (LocalDate dia = PRIMEIRO_DIA; !dia.isAfter(HOJE); dia = dia.plusDays(1)) {
                assertThat(enturmacoes.vigentesEm(aluno.getId(), dia))
                    .as("%s em %s", aluno.getCodigoPublico(), dia).hasSize(1);
            }
        }
    }

    @Test
    void umAlunoTrocaDeTurmaNoMeioDoIntervalo() {
        var trocaram = alunos.findAll().stream()
            .filter(a -> enturmacoes.findByAlunoIdOrderByDataInicioDesc(a.getId()).size() == 2)
            .toList();
        assertThat(trocaram).hasSize(1);

        List<Enturmacao> vinculos = enturmacoes.findByAlunoIdOrderByDataInicioDesc(trocaram.get(0).getId());
        Enturmacao atual = vinculos.get(0);
        Enturmacao anterior = vinculos.get(1);
        assertThat(anterior.getDataFim()).isNotNull();
        assertThat(atual.getDataFim()).isNull();
        assertThat(atual.getDataInicio()).isEqualTo(anterior.getDataFim().plusDays(1));
        assertThat(atual.getDataInicio()).isAfter(PRIMEIRO_DIA).isBefore(HOJE);
        assertThat(atual.getTurma().getId()).isNotEqualTo(anterior.getTurma().getId());

        List<Object[]> consumos = em.createQuery(
                "select a.dataOperacional, a.turmaId from AuditoriaConsumo a where a.aluno.id = :id",
                Object[].class)
            .setParameter("id", trocaram.get(0).getId()).getResultList();
        assertThat(consumos).anyMatch(c -> ((LocalDate) c[0]).isBefore(atual.getDataInicio()));
        assertThat(consumos).anyMatch(c -> !((LocalDate) c[0]).isBefore(atual.getDataInicio()));
        for (Object[] c : consumos) {
            var esperada = ((LocalDate) c[0]).isBefore(atual.getDataInicio()) ? anterior : atual;
            assertThat(c[1]).isEqualTo(esperada.getTurma().getId());
        }
    }

    @Test
    void nenhumConsumoGeradoCaiNoBaldeSemTurma() {
        var todas = auditorias.findAll();
        assertThat(todas).isNotEmpty();
        assertThat(todas).allSatisfy(a -> {
            assertThat(a.getTurmaId()).isNotNull();
            assertThat(a.getTurmaNome()).isNotEqualTo(AuditoriaConsumo.SEM_TURMA);
        });
    }

    @Test
    void repeticoesSaemDeZeroSemSubirAlunosUnicosEESeguemPadraoPorTurma() {
        List<Object[]> grupos = em.createQuery("""
                select a.dataOperacional, a.turno, a.turmaNome, count(a), count(distinct a.aluno.id)
                from AuditoriaConsumo a
                where a.resultado = br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo.AUTORIZADO
                group by a.dataOperacional, a.turno, a.turmaNome
                """, Object[].class).getResultList();

        java.util.Map<String, Long> repeticoesPorTurma = new java.util.TreeMap<>();
        for (Object[] g : grupos) {
            long autorizacoes = (Long) g[3];
            long unicos = (Long) g[4];
            assertThat(unicos).isLessThanOrEqualTo(autorizacoes);
            repeticoesPorTurma.merge((String) g[2], autorizacoes - unicos, Long::sum);
        }

        assertThat(repeticoesPorTurma.values()).anyMatch(r -> r > 0);
        assertThat(grupos).anyMatch(g -> (Long) g[4] < (Long) g[3]);

        long iniciais = soma(repeticoesPorTurma, "1A", "2A", "3A");
        long finais = soma(repeticoesPorTurma, "7A", "8A", "9A");
        assertThat(iniciais).isGreaterThan(finais);
    }

    @Test
    void segundaBipagemEDoMesmoAlunoNaMesmaTurmaESemChaveUnicaDuplicada() {
        List<Object[]> pares = em.createQuery("""
                select a.aluno.id, a.dataOperacional, a.turno, count(a), count(distinct a.turmaId),
                       count(a.chaveConsumoAutorizado)
                from AuditoriaConsumo a
                where a.resultado = br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo.AUTORIZADO
                group by a.aluno.id, a.dataOperacional, a.turno
                having count(a) > 1
                """, Object[].class).getResultList();

        assertThat(pares).isNotEmpty();
        for (Object[] p : pares) {
            assertThat((Long) p[3]).isEqualTo(2L);
            assertThat((Long) p[4]).as("turma carimbada igual nas duas").isEqualTo(1L);
            assertThat((Long) p[5]).as("chave unica so na primeira").isEqualTo(1L);
        }
    }

    private static long soma(java.util.Map<String, Long> mapa, String... turmas) {
        long total = 0;
        for (String t : turmas) total += mapa.getOrDefault(t, 0L);
        return total;
    }
}
