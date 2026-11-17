package br.com.frankcardoso.merenda.gestao.application;

import static br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao.ALMOCO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.refeicao.application.GradeRefeicoesService;
import br.com.frankcardoso.merenda.refeicao.domain.TipoRefeicao;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsolidacaoConsumoServiceTest {

    private static final LocalDate DATA = LocalDate.of(2026, 8, 26);

    @Mock
    private CardapioRepository cardapioRepository;

    @Mock
    private AuditoriaConsumoRepository auditoriaRepository;

    @Mock
    private GradeRefeicoesService grade;

    private ConsolidacaoConsumoService service;

    @BeforeEach
    void prepararService() {
        service = new ConsolidacaoConsumoService(
            cardapioRepository, auditoriaRepository, grade, new ObjectMapper().findAndRegisterModules());
        when(grade.buscar(ALMOCO.id())).thenReturn(new TipoRefeicao(ALMOCO.id(), "Almoço", 3,
            LocalTime.of(11, 30), LocalTime.of(13, 0), Set.of(Turno.INTEGRAL)));
    }

    @Test
    void deveCalcularIndicadoresSemConsultarIa() {
        var cardapio = new Cardapio(UUID.randomUUID(), DATA, ALMOCO.id(), "Arroz e feijao",
            "Teste", "[{\"nome\":\"arroz\"},{\"nome\":\"feijao\"}]", 100, true);
        when(cardapioRepository.findByDataAndTipoRefeicaoIdAndAtivoTrue(DATA, ALMOCO.id()))
            .thenReturn(Optional.of(cardapio));
        when(auditoriaRepository.countByDataOperacionalAndTipoRefeicaoIdAndResultado(
            DATA, ALMOCO.id(), ResultadoConsumo.AUTORIZADO)).thenReturn(75L);
        when(auditoriaRepository.countByDataOperacionalAndTipoRefeicaoIdAndResultado(
            DATA, ALMOCO.id(), ResultadoConsumo.BLOQUEADO)).thenReturn(8L);

        var resultado = service.consolidar(DATA, ALMOCO.id());

        assertThat(resultado.consumosAutorizados()).isEqualTo(75);
        assertThat(resultado.tentativasBloqueadas()).isEqualTo(8);
        assertThat(resultado.taxaConsumoPlanejado()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(resultado.sobraEstimada()).isEqualTo(25);
        assertThat(resultado.itensCardapio()).containsExactly("arroz", "feijao");
    }

    @Test
    void consolidaPorRefeicao() {
        var cardapio = new Cardapio(UUID.randomUUID(), DATA, ALMOCO.id(), "Arroz e feijao",
            "Teste", "[]", 100, true);
        when(cardapioRepository.findByDataAndTipoRefeicaoIdAndAtivoTrue(DATA, ALMOCO.id()))
            .thenReturn(Optional.of(cardapio));
        when(auditoriaRepository.countByDataOperacionalAndTipoRefeicaoIdAndResultado(
            DATA, ALMOCO.id(), ResultadoConsumo.AUTORIZADO)).thenReturn(40L);
        when(auditoriaRepository.countByDataOperacionalAndTipoRefeicaoIdAndResultado(
            DATA, ALMOCO.id(), ResultadoConsumo.BLOQUEADO)).thenReturn(2L);

        var resultado = service.consolidar(DATA, ALMOCO.id());

        assertThat(resultado.tipoRefeicaoId()).isEqualTo(ALMOCO.id());
        assertThat(resultado.refeicao()).isEqualTo("Almoço");
        assertThat(resultado.consumosAutorizados()).isEqualTo(40);
        assertThat(resultado.tentativasBloqueadas()).isEqualTo(2);
    }

    @Test
    void semCardapioDaRefeicaoInformaRefeicaoEData() {
        when(cardapioRepository.findByDataAndTipoRefeicaoIdAndAtivoTrue(DATA, ALMOCO.id()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consolidar(DATA, ALMOCO.id()))
            .isInstanceOf(CardapioNaoEncontradoException.class)
            .hasMessage("Não existe cardápio ativo para Almoço em 2026-08-26.");
    }
}
