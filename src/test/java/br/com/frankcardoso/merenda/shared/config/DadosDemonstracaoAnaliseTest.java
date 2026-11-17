package br.com.frankcardoso.merenda.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticosInput;
import br.com.frankcardoso.merenda.gestao.application.IndicadoresDadosService;
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
import java.text.Normalizer;
import java.util.HashMap;
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
class DadosDemonstracaoAnaliseTest {

    private static final int DIAS = 30;
    private static final int ALUNOS = 60;
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
    @Autowired IndicadoresDadosService indicadores;

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

    private static final List<String> POUCO_ACEITOS =
        List.of("beterraba", "chuchu", "couve", "brocolis", "repolho", "cenoura");
    private static final List<String> SOBRA_ALTA = List.of("arroz", "macarrao", "pao");

    @Test
    void cadaRefeicaoComTurmaTemVinteDiasDeFechamentoNaJanela() {
        for (var refeicao : List.of(RefeicaoPadrao.CAFE_DA_MANHA, RefeicaoPadrao.LANCHE_DA_MANHA,
            RefeicaoPadrao.ALMOCO, RefeicaoPadrao.LANCHE_DA_TARDE)) {
            assertThat(medicoes.countFechamentos(HOJE.minusDays(29), HOJE, refeicao.id()))
                .as(refeicao.nome()).isGreaterThanOrEqualTo(20);
        }
    }

    @Test
    void perfisDeReceitaDaoAceitacaoESobraDistintas() {
        Map<UUID, String> nomes = new HashMap<>();
        receitas.findAll().forEach(r -> nomes.put(r.getId(), r.getNome()));
        Map<UUID, long[]> somas = new HashMap<>();
        Map<UUID, Integer> fechamentos = new HashMap<>();
        for (var m : medicoes.findAll()) {
            if (m.getData().isBefore(HOJE.minusDays(29))) continue;
            long[] s = somas.computeIfAbsent(m.getReceitaId(), k -> new long[4]);
            s[0] += m.getPorcoesPreparadas();
            s[1] += m.getPorcoesServidas();
            s[2] += m.getRestoNoPrato();
            fechamentos.merge(m.getReceitaId(), 1, Integer::sum);
        }

        int pouco = 0, sobra = 0, demais = 0;
        for (var entrada : somas.entrySet()) {
            if (fechamentos.get(entrada.getKey()) < 3) continue;
            String nome = semAcento(nomes.get(entrada.getKey()));
            long[] s = entrada.getValue();
            double aceitacao = (double) (s[1] - s[2]) / s[1];
            double sobraNaPanela = (double) (s[0] - s[1]) / s[0];
            if (POUCO_ACEITOS.stream().anyMatch(nome::contains)) {
                pouco++;
                assertThat(aceitacao).as(nome).isLessThan(0.85);
            } else if (SOBRA_ALTA.stream().anyMatch(nome::contains)) {
                sobra++;
                assertThat(sobraNaPanela).as(nome).isGreaterThanOrEqualTo(0.12);
            } else {
                demais++;
                assertThat(aceitacao).as(nome).isGreaterThanOrEqualTo(0.90);
                assertThat(sobraNaPanela).as(nome).isLessThan(0.10);
            }
        }
        assertThat(pouco).as("itens pouco aceitos").isPositive();
        assertThat(sobra).as("itens com sobra alta").isPositive();
        assertThat(demais).as("demais itens").isPositive();
    }

    @Test
    void almocoTemBeterrabaEChuchuComFechamentosEBaixaAceitacaoComoAAnaliseCalcula() {
        var fechamentos = indicadores(RefeicaoPadrao.ALMOCO).fechamentosDoMes();
        for (var nome : List.of("Beterraba cozida", "Chuchu")) {
            var item = fechamentos.stream().filter(f -> f.nome().equals(nome)).findFirst().orElseThrow();
            assertThat(item.fechamentos()).as(nome).isGreaterThanOrEqualTo(3);
            assertThat(aceitacao(item)).as(nome).isLessThan(0.90);
        }
    }

    @Test
    void refeicoesDeLancheETemItemComSobraAltaEFechamentosSuficientes() {
        for (var refeicao : List.of(RefeicaoPadrao.CAFE_DA_MANHA, RefeicaoPadrao.LANCHE_DA_MANHA,
            RefeicaoPadrao.LANCHE_DA_TARDE)) {
            assertThat(indicadores(refeicao).fechamentosDoMes())
                .as(refeicao.nome())
                .anyMatch(f -> f.fechamentos() >= 3
                    && (double) f.sobraNaoDistribuida() / f.porcoesPreparadas() >= 0.10);
        }
    }

    @Test
    void atendimentoEnviadoParaAnaliseFicaNasFaixasDaDemonstracao() {
        assertThat(atendimento(RefeicaoPadrao.ALMOCO)).isGreaterThanOrEqualTo(0.83);
        assertThat(atendimento(RefeicaoPadrao.LANCHE_DA_MANHA)).isGreaterThanOrEqualTo(0.83);
        assertThat(atendimento(RefeicaoPadrao.CAFE_DA_MANHA)).isLessThanOrEqualTo(0.75);
        assertThat(atendimento(RefeicaoPadrao.LANCHE_DA_TARDE)).isLessThanOrEqualTo(0.75);
    }

    @Test
    void recarregarNaoAlteraOCardapioDoAlmoco() {
        var antes = cardapios.findByDataAndTipoRefeicaoIdAndAtivoTrue(HOJE.minusDays(1), RefeicaoPadrao.ALMOCO.id())
            .orElseThrow().getItensJson();
        carregarDemonstracao();
        assertThat(cardapios.findByDataAndTipoRefeicaoIdAndAtivoTrue(HOJE.minusDays(1), RefeicaoPadrao.ALMOCO.id())
            .orElseThrow().getItensJson()).isEqualTo(antes);
    }

    private IndicadoresLogisticosInput indicadores(RefeicaoPadrao refeicao) {
        return indicadores.preparar(HOJE, refeicao.id());
    }

    private static double aceitacao(IndicadoresLogisticosInput.ReceitaFechamento f) {
        return (double) (f.porcoesServidas() - f.restoNoPrato()) / f.porcoesServidas();
    }

    private double atendimento(RefeicaoPadrao refeicao) {
        var dias = indicadores(refeicao).atendimentoDiario();
        assertThat(dias.size()).as(refeicao.nome()).isGreaterThanOrEqualTo(20);
        double atendidos = dias.stream().mapToLong(IndicadoresLogisticosInput.AtendimentoDia::atendidos).sum();
        double planejadas = dias.stream().mapToLong(IndicadoresLogisticosInput.AtendimentoDia::planejadas).sum();
        return atendidos / planejadas;
    }

    private static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }
}
