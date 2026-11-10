package br.com.frankcardoso.merenda.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.catalogo.infrastructure.ReceitaRepository;
import br.com.frankcardoso.merenda.fila.domain.AuditoriaConsumo;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.medicao.infrastructure.MedicaoSobraRepository;
import br.com.frankcardoso.merenda.refeicao.api.TipoRefeicaoRequest;
import br.com.frankcardoso.merenda.refeicao.application.GradeRefeicoesService;
import br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao;
import br.com.frankcardoso.merenda.turma.application.EnturmacaoService;
import br.com.frankcardoso.merenda.turma.domain.Enturmacao;
import br.com.frankcardoso.merenda.turma.infrastructure.EnturmacaoRepository;
import br.com.frankcardoso.merenda.turma.infrastructure.TurmaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    private static final int DIAS = 30;
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
    @Autowired GradeRefeicoesService grade;
    @Autowired EntityManager em;

    private Clock relogio;

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
        var comRosto = new Aluno(UUID.randomUUID(), "ALU-9001", "20269001", "Aluno Ficticio", true, null,
            Instant.now());
        comRosto.cadastrarRosto("[1.0,0.0]");
        alunos.saveAndFlush(comRosto);
        relogio = Clock.fixed(
            HOJE.atTime(10, 0).atZone(ApplicationConfig.ZONA_OPERACIONAL).toInstant(),
            ApplicationConfig.ZONA_OPERACIONAL);
        new DadosDemonstracaoLoader(alunos, cardapios, auditorias, medicoes, catalogo, receitas,
            turmas, enturmacoes, enturmacaoService, grade, objectMapper, relogio, DIAS, ALUNOS).carregar();
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
    void criaNoveTurmasComRegimeParcialEIntegral() {
        Map<String, Turno> turnoPorCodigo = new java.util.HashMap<>();
        turmas.findAll().forEach(t -> turnoPorCodigo.put(t.getCodigo(), t.getTurno()));
        assertThat(turnoPorCodigo).isEqualTo(Map.of(
            "1A", Turno.MANHA, "2A", Turno.MANHA, "3A", Turno.MANHA,
            "4A", Turno.TARDE, "5A", Turno.TARDE, "6A", Turno.TARDE,
            "7A", Turno.INTEGRAL, "8A", Turno.INTEGRAL, "9A", Turno.INTEGRAL));
    }

    @Test
    void consumoSoParaQuemTemDireitoNaRefeicao() {
        List<Object[]> linhas = em.createQuery("""
                select a.tipoRefeicaoId, t.turno
                from AuditoriaConsumo a join Turma t on t.id = a.turmaId
                where a.resultado = br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo.AUTORIZADO
                """, Object[].class).getResultList();

        assertThat(linhas).isNotEmpty();
        for (Object[] linha : linhas) {
            var refeicao = grade.buscar((UUID) linha[0]);
            assertThat(refeicao.getTurnos()).as(refeicao.getNome()).contains((Turno) linha[1]);
        }
    }

    @Test
    void consumoCaiDentroDaJanelaDaRefeicao() {
        List<Object[]> primeiras = em.createQuery("""
                select a.tipoRefeicaoId, a.instante from AuditoriaConsumo a
                where a.chaveConsumoAutorizado is not null
                """, Object[].class).getResultList();

        assertThat(primeiras).isNotEmpty();
        for (Object[] primeira : primeiras) {
            var refeicao = grade.buscar((UUID) primeira[0]);
            LocalTime hora = ((Instant) primeira[1]).atZone(ApplicationConfig.ZONA_OPERACIONAL).toLocalTime();
            assertThat(hora).as(refeicao.getNome())
                .isAfterOrEqualTo(refeicao.getInicio())
                .isBefore(refeicao.getFim());
        }
    }

    @Test
    void alunoIntegralConsomeEmMaisDeUmaRefeicaoNoMesmoDia() {
        List<Object[]> grupos = em.createQuery("""
                select a.aluno.id, a.dataOperacional
                from AuditoriaConsumo a
                where a.resultado = br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo.AUTORIZADO
                group by a.aluno.id, a.dataOperacional
                having count(distinct a.tipoRefeicaoId) >= 2
                """, Object[].class).getResultList();

        assertThat(grupos).isNotEmpty();
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
                select a.dataOperacional, a.tipoRefeicaoId, a.turmaNome, count(a), count(distinct a.aluno.id)
                from AuditoriaConsumo a
                where a.resultado = br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo.AUTORIZADO
                group by a.dataOperacional, a.tipoRefeicaoId, a.turmaNome
                """, Object[].class).getResultList();

        java.util.Map<String, Long> repeticoesPorTurma = new java.util.TreeMap<>();
        java.util.Map<String, Long> unicosPorTurma = new java.util.TreeMap<>();
        for (Object[] g : grupos) {
            long autorizacoes = (Long) g[3];
            long unicos = (Long) g[4];
            assertThat(unicos).isLessThanOrEqualTo(autorizacoes);
            repeticoesPorTurma.merge((String) g[2], autorizacoes - unicos, Long::sum);
            unicosPorTurma.merge((String) g[2], unicos, Long::sum);
        }

        assertThat(repeticoesPorTurma.values()).anyMatch(r -> r > 0);
        assertThat(grupos).anyMatch(g -> (Long) g[4] < (Long) g[3]);

        // Taxa, nao contagem: aluno integral come em quatro refeicoes e repetiria mais em numero absoluto.
        double iniciais = (double) soma(repeticoesPorTurma, "1A", "2A", "3A") / soma(unicosPorTurma, "1A", "2A", "3A");
        double finais = (double) soma(repeticoesPorTurma, "7A", "8A", "9A") / soma(unicosPorTurma, "7A", "8A", "9A");
        assertThat(iniciais).isGreaterThan(finais);
    }

    @Test
    void segundaBipagemEDoMesmoAlunoNaMesmaTurmaESemChaveUnicaDuplicada() {
        List<Object[]> pares = em.createQuery("""
                select a.aluno.id, a.dataOperacional, a.tipoRefeicaoId, count(a), count(distinct a.turmaId),
                       count(a.chaveConsumoAutorizado)
                from AuditoriaConsumo a
                where a.resultado = br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo.AUTORIZADO
                group by a.aluno.id, a.dataOperacional, a.tipoRefeicaoId
                having count(a) > 1
                """, Object[].class).getResultList();

        assertThat(pares).isNotEmpty();
        for (Object[] p : pares) {
            assertThat((Long) p[3]).isEqualTo(2L);
            assertThat((Long) p[4]).as("turma carimbada igual nas duas").isEqualTo(1L);
            assertThat((Long) p[5]).as("chave unica so na primeira").isEqualTo(1L);
        }
    }

    @Test
    void nenhumConsumoGeradoDepoisDoInstanteAtual() {
        assertThat(auditorias.findAll()).isNotEmpty()
            .allSatisfy(a -> assertThat(a.getInstante()).isBeforeOrEqualTo(relogio.instant()));
    }

    @Test
    void alunoComRostoEntraNaTurmaIntegralEFicaForaDoSintetico() {
        var aluno = alunos.findByCodigoPublico("ALU-9001").orElseThrow();
        Long auditorias = em.createQuery("select count(a) from AuditoriaConsumo a where a.aluno.id = :id",
            Long.class).setParameter("id", aluno.getId()).getSingleResult();
        assertThat(auditorias).isZero();
        assertThat(enturmacoes.vigentesEm(aluno.getId(), HOJE)).singleElement()
            .satisfies(vinculo -> assertThat(vinculo.getTurma().getCodigo()).isEqualTo("7A"));
    }

    @Test
    void alunoForaDoPadraoDaDemonstracaoNaoRecebeConsumoNemEnturmacao() {
        var naTurma = alunos.saveAndFlush(new Aluno(UUID.randomUUID(), "TESTE-01", "20269101", "Aluno Ficticio Um",
            true, null, Instant.now()));
        var semTurma = alunos.saveAndFlush(new Aluno(UUID.randomUUID(), "TESTE-02", "20269102", "Aluno Ficticio Dois",
            true, null, Instant.now()));
        enturmacaoService.enturmar(naTurma.getId(), turmas.findByCodigo("1A").orElseThrow().getId(),
            PRIMEIRO_DIA.minusDays(1));
        auditorias.deleteAll();
        em.flush();

        new DadosDemonstracaoLoader(alunos, cardapios, auditorias, medicoes, catalogo, receitas,
            turmas, enturmacoes, enturmacaoService, grade, objectMapper, relogio, DIAS, ALUNOS).carregar();

        assertThat(auditorias.count()).isPositive();
        Long consumos = em.createQuery("select count(a) from AuditoriaConsumo a where a.aluno.id = :id",
            Long.class).setParameter("id", naTurma.getId()).getSingleResult();
        assertThat(consumos).isZero();
        assertThat(enturmacoes.findByAlunoIdOrderByDataInicioDesc(semTurma.getId())).isEmpty();
    }

    @Test
    void cardapioDeHojeExisteEmTodasAsRefeicoes() {
        for (var refeicao : RefeicaoPadrao.values()) {
            assertThat(cardapios.findByDataAndTipoRefeicaoIdAndAtivoTrue(HOJE, refeicao.id()))
                .as(refeicao.nome()).isPresent();
        }
    }

    @Test
    void refeicoesComTurmaTemVinteDiasDeFechamentoNosUltimosTrintaDias() {
        for (var refeicao : List.of(RefeicaoPadrao.CAFE_DA_MANHA, RefeicaoPadrao.LANCHE_DA_MANHA,
            RefeicaoPadrao.ALMOCO, RefeicaoPadrao.LANCHE_DA_TARDE)) {
            assertThat(medicoes.countFechamentos(HOJE.minusDays(29), HOJE, refeicao.id()))
                .as(refeicao.nome()).isGreaterThanOrEqualTo(20);
        }
    }

    @Test
    void subidaPosteriorMedeLancheDaNoiteEFimDeSemanaDeSubidasAnteriores() {
        var noite = RefeicaoPadrao.LANCHE_DA_NOITE;
        var sabado = HOJE.minusDays(4);
        var terca = HOJE.minusDays(1);
        assertThat(sabado.getDayOfWeek()).isEqualTo(java.time.DayOfWeek.SATURDAY);
        for (var dia : List.of(sabado, terca)) {
            cardapios.saveAndFlush(new br.com.frankcardoso.merenda.cardapio.domain.Cardapio(UUID.randomUUID(), dia,
                noite.id(), "Lanche", "Cardapio de demonstracao",
                catalogo.sortearCardapio(new java.util.Random(7)).comoJson(), 30, true));
            assertThat(medicoes.countFechamentos(dia, dia, noite.id())).isZero();
        }

        new DadosDemonstracaoLoader(alunos, cardapios, auditorias, medicoes, catalogo, receitas,
            turmas, enturmacoes, enturmacaoService, grade, objectMapper, relogio, DIAS, ALUNOS).carregar();

        for (var dia : List.of(sabado, terca)) {
            var medidas = medicoes.findAll().stream()
                .filter(m -> m.getData().equals(dia) && m.getTipoRefeicaoId().equals(noite.id())).toList();
            assertThat(medidas).as(dia.toString()).isNotEmpty();
            var fimDaJanela = dia.atTime(grade.buscar(noite.id()).getFim())
                .atZone(ApplicationConfig.ZONA_OPERACIONAL).toInstant();
            assertThat(medidas).allSatisfy(m -> assertThat(m.getRegistradoEm()).isEqualTo(fimDaJanela));
        }
        assertThat(medicoes.findAll()).noneMatch(m -> m.getData().equals(HOJE));
    }

    @Test
    void nenhumaRefeicaoDeHojeVemFechada() {
        assertThat(medicoes.findAll()).noneMatch(m -> m.getData().equals(HOJE));
        for (var refeicao : RefeicaoPadrao.values()) {
            assertThat(medicoes.countFechamentos(HOJE, HOJE, refeicao.id())).as(refeicao.nome()).isZero();
        }
        assertThat(medicoes.countFechamentos(HOJE.minusDays(1), HOJE.minusDays(1), RefeicaoPadrao.ALMOCO.id()))
            .isEqualTo(1);
    }

    @Test
    void refeicaoCriadaOuTurnoAcrescentadoNaTelaNaoGanhaHistoricoSintetico() {
        var extra = grade.criar(new TipoRefeicaoRequest("Lanche extra", LocalTime.of(14, 0), LocalTime.of(14, 30),
            Set.of(Turno.TARDE)));
        var noite = grade.buscar(RefeicaoPadrao.LANCHE_DA_NOITE.id());
        grade.atualizar(noite.getId(), new TipoRefeicaoRequest(noite.getNome(), noite.getInicio(), noite.getFim(),
            Set.of(Turno.NOITE, Turno.TARDE)));
        auditorias.deleteAll();
        medicoes.deleteAll();
        cardapios.deleteAll();
        em.flush();

        new DadosDemonstracaoLoader(alunos, cardapios, auditorias, medicoes, catalogo, receitas,
            turmas, enturmacoes, enturmacaoService, grade, objectMapper, relogio, DIAS, ALUNOS).carregar();

        assertThat(auditorias.count()).isPositive();
        assertThat(cardapios.findByDataAndTipoRefeicaoIdAndAtivoTrue(HOJE, extra.getId())).isPresent();
        for (var id : List.of(extra.getId(), noite.getId())) {
            assertThat(cardapios.findAll()).noneMatch(c -> c.getTipoRefeicaoId().equals(id) && c.getData().isBefore(HOJE));
            assertThat(medicoes.findAll()).noneMatch(m -> m.getTipoRefeicaoId().equals(id));
            assertThat(auditorias.findAll()).noneMatch(a -> a.getTipoRefeicaoId().equals(id));
        }
    }

    @Test
    void refeicaoSemTurnosNaoGanhaCardapioDeHoje() {
        var noite = grade.buscar(RefeicaoPadrao.LANCHE_DA_NOITE.id());
        grade.atualizar(noite.getId(), new TipoRefeicaoRequest(noite.getNome(), noite.getInicio(), noite.getFim(),
            Set.of()));
        auditorias.deleteAll();
        cardapios.deleteAll();
        em.flush();

        new DadosDemonstracaoLoader(alunos, cardapios, auditorias, medicoes, catalogo, receitas,
            turmas, enturmacoes, enturmacaoService, grade, objectMapper, relogio, DIAS, ALUNOS).carregar();

        assertThat(cardapios.findByDataAndTipoRefeicaoIdAndAtivoTrue(HOJE, noite.getId())).isEmpty();
        assertThat(cardapios.findByDataAndTipoRefeicaoIdAndAtivoTrue(HOJE, RefeicaoPadrao.ALMOCO.id())).isPresent();
    }

    private static long soma(java.util.Map<String, Long> mapa, String... turmas) {
        long total = 0;
        for (String t : turmas) total += mapa.getOrDefault(t, 0L);
        return total;
    }
}
