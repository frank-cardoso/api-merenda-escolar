package br.com.frankcardoso.merenda.historico.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.historico.infrastructure.HistoricoConsumoRepository;
import br.com.frankcardoso.merenda.historico.infrastructure.HistoricoConsumoRepository.ItemAgregado;
import br.com.frankcardoso.merenda.historico.infrastructure.HistoricoConsumoRepository.ItemDiaAgregado;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HistoricoConsumoServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 14);

    @Mock
    private HistoricoConsumoRepository repository;

    private HistoricoConsumoService service() {
        return new HistoricoConsumoService(repository);
    }

    private ItemAgregado item(String nome, long planejado, long servido) {
        return new ItemAgregado() {
            public String getItem() { return nome; }
            public long getVezesPlanejado() { return planejado; }
            public long getVezesServido() { return servido; }
        };
    }

    private ItemDiaAgregado itemDia(String nome, LocalDate data, long planejado, long servido) {
        return new ItemDiaAgregado() {
            public String getItem() { return nome; }
            public LocalDate getData() { return data; }
            public long getPlanejado() { return planejado; }
            public long getServido() { return servido; }
        };
    }

    @Test
    void deveCasarNomeDoCardapioComNomeCompletoDoHistoricoIgnorandoCaseEAcento() {
        when(repository.taxaPorTodosOsItens(Turno.MANHA, HOJE)).thenReturn(List.of(
            item("Macarrao ao sugo", 20, 10),
            item("Arroz branco", 30, 27)
        ));

        var itens = service().itensDoCardapioComTaxa(HOJE, Turno.MANHA, List.of("macarrao"));

        assertThat(itens).extracting("item").containsExactly("Macarrao ao sugo");
    }

    @Test
    void naoDeveRetornarItemQuandoNaoHaCorrespondenciaNoHistorico() {
        when(repository.taxaPorTodosOsItens(Turno.MANHA, HOJE)).thenReturn(List.of(
            item("Macarrao ao sugo", 20, 10),
            item("Arroz branco", 30, 27)
        ));

        var itens = service().itensDoCardapioComTaxa(HOJE, Turno.MANHA, List.of("peixe"));

        assertThat(itens).isEmpty();
    }

    @Test
    void naoDeveConsultarRepositorioParaCardapioSemItens() {
        var itens = service().itensDoCardapioComTaxa(HOJE, Turno.MANHA, List.of());

        assertThat(itens).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void deveAgruparSerieDiariaPorItemMantendoOrdemCronologica() {
        when(repository.serieDiariaPorItem(Turno.MANHA, HOJE, List.of("Macarrao ao sugo")))
            .thenReturn(List.of(
                itemDia("Macarrao ao sugo", HOJE.minusDays(3), 20, 18),
                itemDia("Macarrao ao sugo", HOJE.minusDays(1), 20, 8)
            ));

        var serie = service().serieDiariaDosItens(HOJE, Turno.MANHA, List.of("Macarrao ao sugo"));

        assertThat(serie).hasSize(1);
        assertThat(serie.getFirst().item()).isEqualTo("Macarrao ao sugo");
        assertThat(serie.getFirst().taxasExecucao())
            .containsExactly(new BigDecimal("90.0"), new BigDecimal("40.0"));
    }

    @Test
    void naoDeveConsultarRepositorioQuandoNaoHaItensResolvidos() {
        var serie = service().serieDiariaDosItens(HOJE, Turno.MANHA, List.of());

        assertThat(serie).isEmpty();
        verifyNoInteractions(repository);
    }
}
