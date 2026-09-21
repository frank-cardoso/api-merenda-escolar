package br.com.frankcardoso.merenda.gestao.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticosInput;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticosPort;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class IndicadoresLogisticosServiceTest {
    @Test
    void pythonForaDoArRetornaEstadoExplicitoSemInventarMetricas() {
        var dados = mock(IndicadoresDadosService.class);
        var port = mock(IndicadoresLogisticosPort.class);
        var data = LocalDate.of(2026, 9, 17);
        var input = new IndicadoresLogisticosInput(data, data.minusDays(29), Turno.NOITE,
            100, 80, 70, BigDecimal.valueOf(80), List.of(), List.of(), null, List.of());
        when(dados.preparar(data, Turno.NOITE)).thenReturn(input);
        when(port.calcular(input)).thenThrow(new ResourceAccessException("offline"));
        var output = new IndicadoresLogisticosService(dados, port).calcular(data, Turno.NOITE);
        assertThat(output.status()).isEqualTo("INDISPONIVEL");
        assertThat(output.execucaoPlanejamento()).isNull();
        assertThat(output.topComidas()).isEmpty();
        assertThat(output.avisos()).isNotEmpty();
    }
}
