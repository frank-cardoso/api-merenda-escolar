package br.com.frankcardoso.merenda.medicao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.catalogo.infrastructure.ReceitaRepository;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.medicao.api.MedicaoSobraRequest;
import br.com.frankcardoso.merenda.medicao.domain.MedicaoSobra;
import br.com.frankcardoso.merenda.medicao.domain.MedicaoSobraInconsistenteException;
import br.com.frankcardoso.merenda.medicao.infrastructure.MedicaoSobraRepository;
import br.com.frankcardoso.merenda.refeicao.application.GradeRefeicoesService;
import br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao;
import br.com.frankcardoso.merenda.refeicao.domain.TipoRefeicao;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MedicaoSobraServiceTest {

    private static final LocalDate DATA = LocalDate.of(2026, 3, 10);
    private static final UUID RECEITA = UUID.randomUUID();
    private static final int ESCOLA = 1;

    private final MedicaoSobraRepository repository = mock(MedicaoSobraRepository.class);
    private final ReceitaRepository receitas = mock(ReceitaRepository.class);
    private final CardapioRepository cardapios = mock(CardapioRepository.class);
    private final GradeRefeicoesService grade = mock(GradeRefeicoesService.class);
    private MedicaoSobraService service;

    @BeforeEach
    void preparar() {
        service = new MedicaoSobraService(repository, receitas, cardapios, grade, new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-03-10T18:00:00Z"), ZoneOffset.UTC), ESCOLA);
        when(receitas.existsById(RECEITA)).thenReturn(true);
        when(repository.save(any(MedicaoSobra.class))).thenAnswer(call -> call.getArgument(0));
        for (var padrao : RefeicaoPadrao.values()) {
            when(grade.buscar(padrao.id())).thenReturn(new TipoRefeicao(padrao.id(), padrao.nome(),
                1, LocalTime.of(7, 0), LocalTime.of(8, 0), Set.of(Turno.INTEGRAL)));
            when(cardapios.findByDataAndTipoRefeicaoIdAndAtivoTrue(DATA, padrao.id()))
                .thenReturn(Optional.of(cardapioCom(padrao)));
        }
    }

    @Test
    void relancarMesmoItemNaMesmaRefeicaoSobrescreve() {
        var idExistente = UUID.randomUUID();
        when(repository.findByDataAndTipoRefeicaoIdAndEscolaIdAndReceitaId(DATA,
            RefeicaoPadrao.ALMOCO.id(), ESCOLA, RECEITA)).thenReturn(Optional.of(
                new MedicaoSobra(idExistente, DATA, RefeicaoPadrao.ALMOCO.id(), ESCOLA, RECEITA, 10, 8,
                    2, 1, "LANCADA", Instant.parse("2026-03-10T15:00:00Z"))));

        service.registrar(pedido(RefeicaoPadrao.ALMOCO));

        var salva = ArgumentCaptor.forClass(MedicaoSobra.class);
        verify(repository).save(salva.capture());
        assertThat(salva.getValue().getId()).isEqualTo(idExistente);
        assertThat(salva.getValue().getTipoRefeicaoId()).isEqualTo(RefeicaoPadrao.ALMOCO.id());
    }

    @Test
    void mesmaReceitaEmRefeicoesDiferentesSaoMedicoesDistintas() {
        var idDoAlmoco = UUID.randomUUID();
        when(repository.findByDataAndTipoRefeicaoIdAndEscolaIdAndReceitaId(DATA,
            RefeicaoPadrao.ALMOCO.id(), ESCOLA, RECEITA)).thenReturn(Optional.of(
                new MedicaoSobra(idDoAlmoco, DATA, RefeicaoPadrao.ALMOCO.id(), ESCOLA, RECEITA, 10, 8,
                    2, 1, "LANCADA", Instant.parse("2026-03-10T15:00:00Z"))));
        when(repository.findByDataAndTipoRefeicaoIdAndEscolaIdAndReceitaId(DATA,
            RefeicaoPadrao.LANCHE_DA_MANHA.id(), ESCOLA, RECEITA)).thenReturn(Optional.empty());

        service.registrar(pedido(RefeicaoPadrao.LANCHE_DA_MANHA));

        var salva = ArgumentCaptor.forClass(MedicaoSobra.class);
        verify(repository).save(salva.capture());
        assertThat(salva.getValue().getId()).isNotEqualTo(idDoAlmoco);
        assertThat(salva.getValue().getTipoRefeicaoId()).isEqualTo(RefeicaoPadrao.LANCHE_DA_MANHA.id());
    }

    @Test
    void fechamentoComRefeicoesDiferentesEhRecusado() {
        var itens = List.of(pedido(RefeicaoPadrao.ALMOCO), pedido(RefeicaoPadrao.LANCHE_DA_MANHA));

        assertThatThrownBy(() -> service.registrarFechamento(itens))
            .isInstanceOf(MedicaoSobraInconsistenteException.class)
            .hasMessage("Todos os itens do fechamento devem usar a mesma data e refeição");
    }

    @Test
    void receitaForaDoCardapioDaRefeicaoEhRecusada() {
        var outra = UUID.randomUUID();
        when(receitas.existsById(outra)).thenReturn(true);
        var pedido = new MedicaoSobraRequest(DATA, RefeicaoPadrao.ALMOCO.id(), outra, 10, 8, 2, 1);

        assertThatThrownBy(() -> service.registrar(pedido))
            .isInstanceOf(MedicaoSobraInconsistenteException.class)
            .hasMessage("Receita %s não pertence ao cardápio de %s (Almoço)".formatted(outra, DATA));
    }

    private static MedicaoSobraRequest pedido(RefeicaoPadrao refeicao) {
        return new MedicaoSobraRequest(DATA, refeicao.id(), RECEITA, 10, 8, 2, 1);
    }

    private static Cardapio cardapioCom(RefeicaoPadrao refeicao) {
        return new Cardapio(UUID.randomUUID(), DATA, refeicao.id(), refeicao.nome(), "desc",
            "[{\"receitaId\":\"%s\"}]".formatted(RECEITA), 100, true);
    }
}
