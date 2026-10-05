package br.com.frankcardoso.merenda.analytics.infrastructure.python;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.frankcardoso.merenda.analytics.application.port.TendenciaItemInput;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PythonTendenciaItemAdapterTest {

    @Test
    void deveEnviarSeriesEDevolverTendenciasPorItem() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new PythonTendenciaItemAdapter(
            restClientBuilder.baseUrl("http://analytics:8000").build());

        server.expect(once(), requestTo("http://analytics:8000/api/v1/tendencias-item"))
            .andExpect(method(POST))
            .andExpect(content().contentType(APPLICATION_JSON))
            .andExpect(content().json("""
                {
                  "itens": [
                    {"item": "Salada de alface", "taxasExecucao": [80.0, 82.0, 40.0, 38.0]}
                  ]
                }
                """))
            .andRespond(withSuccess("""
                {
                  "tendencias": [
                    {"item": "Salada de alface", "tendencia": "QUEDA"}
                  ]
                }
                """, APPLICATION_JSON));

        var resultado = adapter.calcular(List.of(
            new TendenciaItemInput("Salada de alface", List.of(
                new BigDecimal("80.0"), new BigDecimal("82.0"),
                new BigDecimal("40.0"), new BigDecimal("38.0")))
        ));

        assertThat(resultado).hasSize(1);
        assertThat(resultado.getFirst().item()).isEqualTo("Salada de alface");
        assertThat(resultado.getFirst().tendencia()).isEqualTo("QUEDA");
        server.verify();
    }

    @Test
    void naoDeveChamarServicoQuandoNaoHaItens() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new PythonTendenciaItemAdapter(
            restClientBuilder.baseUrl("http://analytics:8000").build());

        var resultado = adapter.calcular(List.of());

        assertThat(resultado).isEmpty();
        server.verify();
    }
}
