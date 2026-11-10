package br.com.frankcardoso.merenda.analytics.infrastructure.python;

import static br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao.ALMOCO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticosInput;
import br.com.frankcardoso.merenda.analytics.application.port.RefeicaoRef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PythonIndicadoresLogisticosAdapterTest {
    private static final String ID_ALMOCO = "5e1a0000-0000-4000-8000-000000000003";

    private IndicadoresLogisticosInput input() {
        return new IndicadoresLogisticosInput(LocalDate.of(2026, 9, 17), LocalDate.of(2026, 8, 19),
            new RefeicaoRef(ALMOCO.id(), "Almoço"), 100, 80, 70, new BigDecimal("80"), List.of(), List.of(),
            null, List.of(), List.of(), List.of(), 0, 0, List.of(), List.of(), List.of(), false, 0,
            List.of(new IndicadoresLogisticosInput.AtendimentoDia(LocalDate.of(2026, 9, 16), 100, 38)));
    }

    @Test
    void enviaContratoAgregadoERecebePercentuaisERepeticoes() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var adapter = new PythonIndicadoresLogisticosAdapter(builder.baseUrl("http://analytics").build());
        server.expect(requestTo("http://analytics/api/v1/indicadores-logisticos"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""
                {"dataReferencia":"2026-09-17","inicioHistorico":"2026-08-19",
                 "refeicao":{"id":"%s","nome":"Almoço"},"historicoSemRefeicao":0,
                 "quantidadePlanejada":100,"consumosRegistrados":80,"alunosUnicos":70,
                 "metaPercentual":80,"turmas":[],"itens":[],
                 "atendimentoDiario":[{"data":"2026-09-16","planejadas":100,"atendidos":38}]}
                """.formatted(ID_ALMOCO)))
            .andExpect(jsonPath("$.turno").doesNotExist())
            .andRespond(withSuccess(respostaComSchema("6", "[]"), MediaType.APPLICATION_JSON));
        var result = adapter.calcular(input());
        assertThat(result.execucaoPlanejamento().percentual()).isEqualByComparingTo("80");
        assertThat(result.atendimentos().repeticoes()).isEqualTo(10);
        assertThat(result.aceitacao().nivel()).isEqualTo("ALTA");
        assertThat(result.desperdicio().risco()).isEqualTo("BAIXO");
        assertThat(result.execucaoPlanejamento().mediaAtendidosDia()).isEqualByComparingTo("38.4");
        assertThat(result.execucaoPlanejamento().mediaPlanejadaDia()).isEqualByComparingTo("101");
        assertThat(result.aceitacaoItens().get(0).sobraPercentual()).isEqualByComparingTo("16.7");
        assertThat(result.planoDeAcao()).singleElement().satisfies(acao -> {
            assertThat(acao.tipo()).isEqualTo("REVISAR_PREPARACAO");
            assertThat(acao.item()).isEqualTo("Sopa de legumes");
            assertThat(acao.valor()).isEqualByComparingTo("82");
            assertThat(acao.referencia()).isEqualByComparingTo("90");
        });
        server.verify();
    }

    private static String respostaComSchema(String schema, String porTurma) {
        return resposta(schema, ID_ALMOCO, porTurma);
    }

    private static String resposta(String schema, String refeicaoId, String porTurma) {
        return """
            {"schemaVersion":"%s","calculoVersao":"indicadores-v1","status":"DISPONIVEL",
             "dataReferencia":"2026-09-17","inicioHistorico":"2026-08-19","turno":null,
             "refeicao":{"id":"%s","nome":"Almoço"},
             "execucaoPlanejamento":{"refeicoesPlanejadas":100,"consumosRegistrados":80,
              "percentual":80,"metaPercentual":80,"diferencaMetaPp":0,"statusMeta":"ATINGIDA",
              "mediaAtendidosDia":38.4,"mediaPlanejadaDia":101},
             "atendimentos":{"alunosUnicos":70,"repeticoes":10},
             "aceitacao":{"status":"DISPONIVEL","nivel":"ALTA","percentual":91.2,
              "porcoesServidas":80,"restoNoPrato":7,"motivo":"m"},
             "desperdicio":{"status":"DISPONIVEL","risco":"BAIXO","percentual":9.5,
              "porcoesPreparadas":84,"sobraNaoDistribuida":4,"restoNoPrato":7,"motivo":"m"},
             "ingredientes":{"status":"DISPONIVEL","basePercentualResto":8.5,"acimaDaBase":[],"motivo":"m"},
             "topComidas":[],"aceitacaoItens":[{"receitaId":"7f0c2a9e-5d1b-4e63-9a47-2b8f1c3d4e5f",
              "item":"Sopa de legumes","porcoesPreparadas":120,"porcoesServidas":100,"porcoesConsumidas":82,
              "restoNoPrato":18,"percentual":82,"sobraPercentual":16.7,"fechamentos":6}],
             "aceitacaoItensSemana":[],
             "porTurma":%s,"avisos":[],
             "planoDeAcao":[{"tipo":"REVISAR_PREPARACAO","receitaId":"7f0c2a9e-5d1b-4e63-9a47-2b8f1c3d4e5f",
              "item":"Sopa de legumes","texto":"Revisar a preparação de Sopa de legumes.",
              "valor":82,"referencia":90}]}
            """.formatted(schema, refeicaoId, porTurma);
    }

    private PythonIndicadoresLogisticosAdapter adapterRespondendo(String corpo) {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://analytics/api/v1/indicadores-logisticos"))
            .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));
        return new PythonIndicadoresLogisticosAdapter(builder.baseUrl("http://analytics").build());
    }

    @Test
    void aceitaRespostaSchema6() {
        var turmaId = "7f0c2a9e-5d1b-4e63-9a47-2b8f1c3d4e5f";
        var porTurma = """
            [{"turmaId":"%s","turma":"5A","consumosRegistrados":40,"alunosUnicos":35,"repeticoes":5,
              "percentual":null,"statusMeta":"NAO_AVALIAVEL","motivo":"m"},
             {"turmaId":null,"turma":"Sem turma","consumosRegistrados":2,"alunosUnicos":2,"repeticoes":0,
              "percentual":null,"statusMeta":"NAO_AVALIAVEL","motivo":"m"}]
            """.formatted(turmaId);
        var result = adapterRespondendo(respostaComSchema("6", porTurma)).calcular(input());
        assertThat(result.schemaVersion()).isEqualTo("6");
        assertThat(result.refeicao()).isEqualTo(new RefeicaoRef(ALMOCO.id(), "Almoço"));
        assertThat(result.turno()).isNull();
        assertThat(result.porTurma()).hasSize(2);
        assertThat(result.porTurma().get(0).turmaId()).isEqualTo(java.util.UUID.fromString(turmaId));
        assertThat(result.porTurma().get(0).turma()).isEqualTo("5A");
        assertThat(result.porTurma().get(1).turmaId()).isNull();
        assertThat(result.porTurma().get(1).turma()).isEqualTo("Sem turma");
    }

    @Test
    void rejeitaRespostaSchema5EAnteriores() {
        for (var schema : List.of("5", "4")) {
            var adapter = adapterRespondendo(respostaComSchema(schema, "[]"));
            assertThatThrownBy(() -> adapter.calcular(input()))
                .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void rejeitaRespostaSemPlanoDeAcao() {
        var semPlano = respostaComSchema("6", "[]").replaceAll("(?s),\\s*\"planoDeAcao\".*\\]\\}\\s*$", "}");
        assertThatThrownBy(() -> adapterRespondendo(semPlano).calcular(input()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejeitaRespostaDeOutraRefeicao() {
        var adapter = adapterRespondendo(resposta("6", "5e1a0000-0000-4000-8000-000000000001", "[]"));
        assertThatThrownBy(() -> adapter.calcular(input()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fallbackIndisponivelUsaSchema6() {
        var refeicao = new RefeicaoRef(ALMOCO.id(), "Almoço");
        var indisponivel = IndicadoresLogisticos.indisponivel(LocalDate.of(2026, 9, 17), refeicao);
        assertThat(indisponivel.schemaVersion()).isEqualTo("6");
        assertThat(indisponivel.refeicao()).isEqualTo(refeicao);
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
