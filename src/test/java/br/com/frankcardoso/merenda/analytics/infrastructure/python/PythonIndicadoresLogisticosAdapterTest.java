package br.com.frankcardoso.merenda.analytics.infrastructure.python;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticosInput;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PythonIndicadoresLogisticosAdapterTest {
    private IndicadoresLogisticosInput input() {
        return new IndicadoresLogisticosInput(LocalDate.of(2026, 9, 17), LocalDate.of(2026, 8, 19),
            Turno.MANHA, 100, 80, 70, new BigDecimal("80"), List.of(), List.of(),
            null, List.of());
    }

    @Test
    void enviaContratoAgregadoERecebePercentuaisERepeticoes() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var adapter = new PythonIndicadoresLogisticosAdapter(builder.baseUrl("http://analytics").build());
        server.expect(requestTo("http://analytics/api/v1/indicadores-logisticos"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""
                {"dataReferencia":"2026-09-17","inicioHistorico":"2026-08-19","turno":"MANHA",
                 "quantidadePlanejada":100,"consumosRegistrados":80,"alunosUnicos":70,
                 "metaPercentual":80,"turmas":[],"itens":[]}
                """))
            .andRespond(withSuccess("""
                {"schemaVersion":"3","calculoVersao":"indicadores-v1","status":"DISPONIVEL",
                 "dataReferencia":"2026-09-17","inicioHistorico":"2026-08-19","turno":"MANHA",
                 "execucaoPlanejamento":{"refeicoesPlanejadas":100,"consumosRegistrados":80,
                  "percentual":80,"metaPercentual":80,"diferencaMetaPp":0,"statusMeta":"ATINGIDA"},
                 "atendimentos":{"alunosUnicos":70,"repeticoes":10},
                 "aceitacao":{"status":"DISPONIVEL","nivel":"ALTA","percentual":91.2,
                  "porcoesServidas":80,"restoNoPrato":7,"motivo":"Medido sobre o servido."},
                 "desperdicio":{"status":"DISPONIVEL","risco":"BAIXO","percentual":9.5,
                  "porcoesPreparadas":84,"sobraNaoDistribuida":4,"restoNoPrato":7,
                  "motivo":"Perda sobre o preparado."},
                 "ingredientes":{"status":"DISPONIVEL","basePercentualResto":8.5,
                  "acimaDaBase":[],"motivo":"Sem ingrediente acima dos pratos sem ele."},
                 "topComidas":[],"porTurma":[],"avisos":[]}
                """, MediaType.APPLICATION_JSON));
        var result = adapter.calcular(input());
        assertThat(result.execucaoPlanejamento().percentual()).isEqualByComparingTo("80");
        assertThat(result.atendimentos().repeticoes()).isEqualTo(10);
        assertThat(result.aceitacao().nivel()).isEqualTo("ALTA");
        assertThat(result.desperdicio().risco()).isEqualTo("BAIXO");
        server.verify();
    }

    @Test
    void rejeitaRespostaIncompletaEmVezDeExibirZeros() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var adapter = new PythonIndicadoresLogisticosAdapter(builder.baseUrl("http://analytics").build());
        server.expect(requestTo("http://analytics/api/v1/indicadores-logisticos"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> adapter.calcular(input())).isInstanceOf(IllegalStateException.class);
    }
}
