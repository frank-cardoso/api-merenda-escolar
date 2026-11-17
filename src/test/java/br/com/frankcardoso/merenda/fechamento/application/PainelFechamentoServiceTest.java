package br.com.frankcardoso.merenda.fechamento.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.fechamento.domain.SituacaoFechamento;
import br.com.frankcardoso.merenda.medicao.domain.MedicaoSobra;
import br.com.frankcardoso.merenda.medicao.infrastructure.MedicaoSobraRepository;
import br.com.frankcardoso.merenda.refeicao.application.GradeRefeicoesService;
import br.com.frankcardoso.merenda.refeicao.domain.TipoRefeicao;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PainelFechamentoServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 3, 10);
    private static final String[] NOMES = {"Café da manhã", "Lanche da manhã", "Almoço", "Lanche da tarde", "Lanche da noite"};
    private static final LocalTime[] INICIOS = {LocalTime.of(7, 0), LocalTime.of(9, 30), LocalTime.of(11, 30),
        LocalTime.of(14, 0), LocalTime.of(18, 0)};

    @Mock
    private GradeRefeicoesService grade;
    @Mock
    private CardapioRepository cardapios;
    @Mock
    private MedicaoSobraRepository medicoes;
    @Mock
    private AuditoriaConsumoRepository auditorias;

    private final List<TipoRefeicao> refeicoes = new ArrayList<>();
    private PainelFechamentoService service;

    @BeforeEach
    void preparar() {
        for (int i = 0; i < 5; i++) {
            refeicoes.add(new TipoRefeicao(UUID.randomUUID(), NOMES[i], i + 1, INICIOS[i],
                INICIOS[i].plusHours(1), Set.of(Turno.INTEGRAL)));
        }
        when(grade.listar()).thenReturn(refeicoes);
        // 2026-03-10T15:00Z = 12:00 em America/Sao_Paulo
        var clock = Clock.fixed(Instant.parse("2026-03-10T15:00:00Z"), ZoneOffset.UTC);
        service = new PainelFechamentoService(grade, cardapios, medicoes, auditorias,
            new ObjectMapper().findAndRegisterModules(), clock);
    }

    private Cardapio cardapio(LocalDate data, TipoRefeicao refeicao, UUID... receitas) {
        var itens = new StringBuilder("[");
        for (var receita : receitas) {
            if (itens.length() > 1) itens.append(',');
            itens.append("{\"nome\":\"item\",\"receitaId\":\"%s\"}".formatted(receita));
        }
        var cardapio = new Cardapio(UUID.randomUUID(), data, refeicao.getId(), "Teste", "Teste",
            itens.append(']').toString(), 100, true);
        when(cardapios.findByDataAndTipoRefeicaoIdAndAtivoTrue(data, refeicao.getId()))
            .thenReturn(Optional.of(cardapio));
        return cardapio;
    }

    private MedicaoSobra medida(LocalDate data, TipoRefeicao refeicao, UUID receita, String registradoEm) {
        return new MedicaoSobra(UUID.randomUUID(), data, refeicao.getId(), 1, receita, 10, 8, 2, 1,
            "MANUAL", Instant.parse(registradoEm));
    }

    private void medicoes(LocalDate data, TipoRefeicao refeicao, MedicaoSobra... medidas) {
        when(medicoes.findAllByDataAndTipoRefeicaoIdAndEscolaIdOrderByReceitaId(data, refeicao.getId(), 1))
            .thenReturn(List.of(medidas));
    }

    @Test
    void doDiaListaTodasAsRefeicoesDaGradeNaOrdem() {
        var almoco = refeicoes.get(2);
        var cardapio = cardapio(HOJE, almoco, UUID.randomUUID());

        var linhas = service.doDia(HOJE);

        assertThat(linhas).extracting("refeicao").containsExactly((Object[]) NOMES);
        assertThat(linhas).extracting("ordem").containsExactly(1, 2, 3, 4, 5);
        var semCardapio = linhas.get(0);
        assertThat(semCardapio.situacao()).isEqualTo(SituacaoFechamento.SEM_CARDAPIO);
        assertThat(semCardapio.cardapioId()).isNull();
        assertThat(semCardapio.itensCardapio()).isZero();
        assertThat(linhas.get(2).cardapioId()).isEqualTo(cardapio.getId());
        assertThat(linhas.get(2).situacao()).isEqualTo(SituacaoFechamento.EM_ANDAMENTO);
    }

    @Test
    void parcialNaoEFechada() {
        var refeicao = refeicoes.get(1);
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var c = UUID.randomUUID();
        cardapio(HOJE, refeicao, a, b, c);
        medicoes(HOJE, refeicao, medida(HOJE, refeicao, a, "2026-03-10T13:00:00Z"),
            medida(HOJE, refeicao, b, "2026-03-10T13:05:00Z"));

        var linha = service.doDia(HOJE).get(1);

        assertThat(linha.situacao()).isEqualTo(SituacaoFechamento.PENDENTE);
        assertThat(linha.itensCardapio()).isEqualTo(3);
        assertThat(linha.itensMedidos()).isEqualTo(2);
        assertThat(linha.fechadoEm()).isNull();
    }

    @Test
    void fechadaTrazOMaiorRegistradoEm() {
        var refeicao = refeicoes.get(1);
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var c = UUID.randomUUID();
        cardapio(HOJE, refeicao, a, b, c);
        medicoes(HOJE, refeicao, medida(HOJE, refeicao, a, "2026-03-10T13:00:00Z"),
            medida(HOJE, refeicao, b, "2026-03-10T13:20:00Z"),
            medida(HOJE, refeicao, c, "2026-03-10T13:05:00Z"));

        var linha = service.doDia(HOJE).get(1);

        assertThat(linha.situacao()).isEqualTo(SituacaoFechamento.FECHADA);
        assertThat(linha.itensMedidos()).isEqualTo(3);
        assertThat(linha.fechadoEm()).isEqualTo(Instant.parse("2026-03-10T13:20:00Z"));
    }

    @Test
    void medicaoDeReceitaForaDoCardapioNaoConta() {
        var refeicao = refeicoes.get(1);
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        cardapio(HOJE, refeicao, a, b);
        medicoes(HOJE, refeicao, medida(HOJE, refeicao, a, "2026-03-10T13:00:00Z"),
            medida(HOJE, refeicao, UUID.randomUUID(), "2026-03-10T13:05:00Z"));

        var linha = service.doDia(HOJE).get(1);

        assertThat(linha.itensMedidos()).isEqualTo(1);
        assertThat(linha.situacao()).isEqualTo(SituacaoFechamento.PENDENTE);
    }

    @Test
    void pendenciasSoTrazPendentesNoIntervalo() {
        var ontem = HOJE.minusDays(1);
        var anteontem = HOJE.minusDays(2);
        // ontem: ordens 2 e 1 pendentes, 3 fechada; hoje: 2 pendente (12:00 após 10:30), 3 em andamento
        cardapio(ontem, refeicoes.get(1), UUID.randomUUID());
        cardapio(ontem, refeicoes.get(0), UUID.randomUUID());
        var receita = UUID.randomUUID();
        cardapio(ontem, refeicoes.get(2), receita);
        medicoes(ontem, refeicoes.get(2), medida(ontem, refeicoes.get(2), receita, "2026-03-09T16:00:00Z"));
        cardapio(HOJE, refeicoes.get(1), UUID.randomUUID());
        cardapio(HOJE, refeicoes.get(2), UUID.randomUUID());
        // fora do intervalo (dias=2): anteontem pendente não entra
        cardapio(anteontem, refeicoes.get(0), UUID.randomUUID());

        var pendencias = service.pendencias(HOJE, 2);

        assertThat(pendencias).extracting("data", "refeicao").containsExactly(
            tuple(ontem, NOMES[0]),
            tuple(ontem, NOMES[1]),
            tuple(HOJE, NOMES[1]));
        assertThat(pendencias.get(0).itensCardapio()).isEqualTo(1);
        assertThat(pendencias.get(0).itensMedidos()).isZero();
    }

    @Test
    void itemComReceitaIdNuloNaoDerrubaNemConta() {
        var refeicao = refeicoes.get(1);
        var receita = UUID.randomUUID();
        var itens = "[{\"nome\":\"item\",\"receitaId\":\"%s\"},{\"nome\":\"sem receita\",\"receitaId\":null}]"
            .formatted(receita);
        when(cardapios.findByDataAndTipoRefeicaoIdAndAtivoTrue(HOJE, refeicao.getId())).thenReturn(Optional.of(
            new Cardapio(UUID.randomUUID(), HOJE, refeicao.getId(), "Teste", "Teste", itens, 100, true)));

        var linha = service.doDia(HOJE).get(1);

        assertThat(linha.itensCardapio()).isEqualTo(1);
    }
}
