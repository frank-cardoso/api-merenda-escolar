package br.com.frankcardoso.merenda.analytics.infrastructure.python;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoInput;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PythonPrevisaoConsumoAdapterTest {

    @Test
    void deveEnviarDadosAgregadosParaServicoPython() {
        var restClientBuilder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(restClientBuilder).build();
        var adapter = new PythonPrevisaoConsumoAdapter(
            restClientBuilder.baseUrl("http://analytics:8000").build());

        server.expect(once(), requestTo("http://analytics:8000/api/v1/previsoes-consumo"))
            .andExpect(method(POST))
            .andExpect(content().contentType(APPLICATION_JSON))
            .andExpect(content().json("""
                {
                  "dataReferencia": "2026-08-26",
                  "turno": "NOITE",
                  "cardapio": "Arroz, feijao e frango",
                  "quantidadePlanejada": 300,
                  "consumosAutorizados": 1,
                  "tentativasBloqueadas": 3,
                  "taxaConsumoPlanejado": 0.33,
                  "sobraDePlanejamento": 299,
                  "historico": []
                }
                """))
            .andRespond(withSuccess("""
                {
                  "demandaEstimada": 1,
                  "mediaHistorica": null,
                  "pisoRealizado": 1,
                  "origemEstimativa": "REALIZADO_SEM_HISTORICO",
                  "diferencaPrevisaoPlanejamento": -299,
                  "riscoDesperdicio": "NAO_AVALIAVEL",
                  "desperdicioMotivo": "Sem medicao de sobra e resto",
                  "confianca": "BAIXA",
                  "metodo": "baseline-estatistico-v2",
                  "evidencias": ["Historico insuficiente"]
                }
                """, APPLICATION_JSON));

        var resultado = adapter.prever(new PrevisaoConsumoInput(
            LocalDate.of(2026, 8, 26),
            Turno.NOITE,
            "Arroz, feijao e frango",
            300,
            1,
            3,
            new BigDecimal("0.33"),
            299
        ));

        assertThat(resultado.demandaEstimada()).isEqualTo(1);
        assertThat(resultado.mediaHistorica()).isNull();
        assertThat(resultado.pisoRealizado()).isEqualTo(1);
        assertThat(resultado.origemEstimativa()).isEqualTo("REALIZADO_SEM_HISTORICO");
        assertThat(resultado.diferencaPrevisaoPlanejamento()).isEqualTo(-299);
        assertThat(resultado.riscoDesperdicio()).isEqualTo("NAO_AVALIAVEL");
        assertThat(resultado.desperdicioMotivo()).isEqualTo("Sem medicao de sobra e resto");
        assertThat(resultado.confianca()).isEqualTo("BAIXA");
        assertThat(resultado.metodo()).isEqualTo("baseline-estatistico-v2");
        assertThat(resultado.evidencias()).containsExactly("Historico insuficiente");
        server.verify();
    }
}
