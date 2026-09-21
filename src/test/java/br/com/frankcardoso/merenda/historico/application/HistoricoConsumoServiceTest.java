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
import java.util.UUID;
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

    private ItemAgregado item(UUID receitaId, String nome, long planejado, long servido) {
        return new ItemAgregado() {
            public UUID getReceitaId() { return receitaId; }
            public String getItem() { return nome; }
            public long getVezesPlanejado() { return planejado; }
            public long getVezesServido() { return servido; }
        };
    }

    private ItemDiaAgregado itemDia(UUID receitaId, String nome, LocalDate data, long planejado,
                                    long servido) {
        return new ItemDiaAgregado() {
            public UUID getReceitaId() { return receitaId; }
            public String getItem() { return nome; }
            public LocalDate getData() { return data; }
            public long getPlanejado() { return planejado; }
            public long getServido() { return servido; }
        };
    }

    @Test
    void deveBuscarAsReceitasDoCardapioPorId() {
        var macarrao = UUID.randomUUID();
        when(repository.taxaPorReceitas(Turno.MANHA, HOJE, List.of(macarrao))).thenReturn(List.of(
            item(macarrao, "Macarrao ao sugo", 20, 10)
        ));

        var itens = service().itensDoCardapioComTaxa(HOJE, Turno.MANHA, List.of(macarrao));

        assertThat(itens).singleElement().satisfies(item -> {
            assertThat(item.receitaId()).isEqualTo(macarrao);
            assertThat(item.item()).isEqualTo("Macarrao ao sugo");
        });
    }

    @Test
    void naoDeveConsultarNadaQuandoOCardapioNaoTemReceita() {
        assertThat(service().itensDoCardapioComTaxa(HOJE, Turno.MANHA, List.of())).isEmpty();
    }

    @Test
    void naoDeveConsultarRepositorioParaCardapioSemItens() {
        var itens = service().itensDoCardapioComTaxa(HOJE, Turno.MANHA, List.of());

        assertThat(itens).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void deveAgruparSerieDiariaPorItemMantendoOrdemCronologica() {
        var macarrao = UUID.randomUUID();
        when(repository.serieDiariaPorReceita(Turno.MANHA, HOJE, List.of(macarrao)))
            .thenReturn(List.of(
                itemDia(macarrao, "Macarrao ao sugo", HOJE.minusDays(3), 20, 18),
                itemDia(macarrao, "Macarrao ao sugo", HOJE.minusDays(1), 20, 8)
            ));

        var serie = service().serieDiariaDosItens(HOJE, Turno.MANHA, List.of(macarrao));

        assertThat(serie).hasSize(1);
        assertThat(serie.getFirst().receitaId()).isEqualTo(macarrao);
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
