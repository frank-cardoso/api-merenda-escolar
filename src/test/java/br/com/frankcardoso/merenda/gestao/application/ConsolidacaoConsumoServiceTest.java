package br.com.frankcardoso.merenda.gestao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsolidacaoConsumoServiceTest {

    @Mock
    private CardapioRepository cardapioRepository;

    @Mock
    private AuditoriaConsumoRepository auditoriaRepository;

    private ConsolidacaoConsumoService service;

    @BeforeEach
    void prepararService() {
        service = new ConsolidacaoConsumoService(
            cardapioRepository, auditoriaRepository, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void deveCalcularIndicadoresSemConsultarIa() {
        var data = LocalDate.of(2026, 8, 26);
        var cardapio = new Cardapio(UUID.randomUUID(), data, Turno.MANHA, "Arroz e feijao",
            "Teste", "[{\"nome\":\"arroz\"},{\"nome\":\"feijao\"}]", 100, true);

        when(cardapioRepository.findByDataAndTurnoAndAtivoTrue(data, Turno.MANHA))
            .thenReturn(Optional.of(cardapio));
        when(auditoriaRepository.countByDataOperacionalAndTurnoAndResultado(
            data, Turno.MANHA, ResultadoConsumo.AUTORIZADO)).thenReturn(75L);
        when(auditoriaRepository.countByDataOperacionalAndTurnoAndResultado(
            data, Turno.MANHA, ResultadoConsumo.BLOQUEADO)).thenReturn(8L);

        var resultado = service.consolidar(data, Turno.MANHA);

        assertThat(resultado.consumosAutorizados()).isEqualTo(75);
        assertThat(resultado.tentativasBloqueadas()).isEqualTo(8);
        assertThat(resultado.taxaConsumoPlanejado()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(resultado.sobraEstimada()).isEqualTo(25);
        assertThat(resultado.itensCardapio()).containsExactly("arroz", "feijao");
    }
}
