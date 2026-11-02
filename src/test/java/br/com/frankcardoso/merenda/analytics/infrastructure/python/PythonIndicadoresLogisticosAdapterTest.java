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
            null, List.of(), List.of(), List.of(), 0, 0, List.of());
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
                {"schemaVersion":"4","calculoVersao":"indicadores-v1","status":"DISPONIVEL",
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
                 "topComidas":[],"aceitacaoItens":[],"aceitacaoItensSemana":[],
                 "porTurma":[],"avisos":[]}
                """, MediaType.APPLICATION_JSON));
        var result = adapter.calcular(input());
        assertThat(result.execucaoPlanejamento().percentual()).isEqualByComparingTo("80");
        assertThat(result.atendimentos().repeticoes()).isEqualTo(10);
        assertThat(result.aceitacao().nivel()).isEqualTo("ALTA");
        assertThat(result.desperdicio().risco()).isEqualTo("BAIXO");
        server.verify();
    }

    private static String respostaComSchema(String schema, String porTurma) {
        return """
            {"schemaVersion":"%s","calculoVersao":"indicadores-v1","status":"DISPONIVEL",
             "dataReferencia":"2026-09-17","inicioHistorico":"2026-08-19","turno":"MANHA",
             "execucaoPlanejamento":{"refeicoesPlanejadas":100,"consumosRegistrados":80,
              "percentual":80,"metaPercentual":80,"diferencaMetaPp":0,"statusMeta":"ATINGIDA"},
             "atendimentos":{"alunosUnicos":70,"repeticoes":10},
             "aceitacao":{"status":"DISPONIVEL","nivel":"ALTA","percentual":91.2,
              "porcoesServidas":80,"restoNoPrato":7,"motivo":"m"},
             "desperdicio":{"status":"DISPONIVEL","risco":"BAIXO","percentual":9.5,
              "porcoesPreparadas":84,"sobraNaoDistribuida":4,"restoNoPrato":7,"motivo":"m"},
             "ingredientes":{"status":"DISPONIVEL","basePercentualResto":8.5,"acimaDaBase":[],"motivo":"m"},
             "topComidas":[],"aceitacaoItens":[],"aceitacaoItensSemana":[],
             "porTurma":%s,"avisos":[]}
            """.formatted(schema, porTurma);
    }

    private PythonIndicadoresLogisticosAdapter adapterRespondendo(String corpo) {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://analytics/api/v1/indicadores-logisticos"))
            .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));
        return new PythonIndicadoresLogisticosAdapter(builder.baseUrl("http://analytics").build());
    }

    @Test
    void aceitaRespostaSchema4() {
        var turmaId = "7f0c2a9e-5d1b-4e63-9a47-2b8f1c3d4e5f";
        var porTurma = """
            [{"turmaId":"%s","turma":"5A","consumosRegistrados":40,"alunosUnicos":35,"repeticoes":5,
              "percentual":null,"statusMeta":"NAO_AVALIAVEL","motivo":"m"},
             {"turmaId":null,"turma":"Sem turma","consumosRegistrados":2,"alunosUnicos":2,"repeticoes":0,
              "percentual":null,"statusMeta":"NAO_AVALIAVEL","motivo":"m"}]
            """.formatted(turmaId);
        var result = adapterRespondendo(respostaComSchema("4", porTurma)).calcular(input());
        assertThat(result.schemaVersion()).isEqualTo("4");
        assertThat(result.porTurma()).hasSize(2);
        assertThat(result.porTurma().get(0).turmaId()).isEqualTo(java.util.UUID.fromString(turmaId));
        assertThat(result.porTurma().get(0).turma()).isEqualTo("5A");
        assertThat(result.porTurma().get(1).turmaId()).isNull();
        assertThat(result.porTurma().get(1).turma()).isEqualTo("Sem turma");
    }

    @Test
    void rejeitaRespostaSchema3() {
        var adapter = adapterRespondendo(respostaComSchema("3", "[]"));
        assertThatThrownBy(() -> adapter.calcular(input()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fallbackIndisponivelUsaSchema4() {
        assertThat(br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos
            .indisponivel(LocalDate.of(2026, 9, 17), Turno.MANHA).schemaVersion()).isEqualTo("4");
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
