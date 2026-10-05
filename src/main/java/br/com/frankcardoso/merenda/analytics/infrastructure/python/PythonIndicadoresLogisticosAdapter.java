package br.com.frankcardoso.merenda.analytics.infrastructure.python;

import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticosInput;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticosPort;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PythonIndicadoresLogisticosAdapter implements IndicadoresLogisticosPort {
    private final RestClient client;

    @Autowired
    public PythonIndicadoresLogisticosAdapter(RestClient.Builder builder,
        @Value("${merenda.analytics.base-url:http://localhost:8000}") String baseUrl,
        @Value("${merenda.analytics.timeout:2s}") Duration timeout) {
        this(builder.baseUrl(baseUrl).requestFactory(requestFactory(timeout)).build());
    }

    PythonIndicadoresLogisticosAdapter(RestClient client) {
        this.client = client;
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }

    @Override
    public IndicadoresLogisticos calcular(IndicadoresLogisticosInput input) {
        var result = client.post().uri("/api/v1/indicadores-logisticos").body(input)
            .retrieve().body(IndicadoresLogisticos.class);
        if (result == null || !"3".equals(result.schemaVersion())
            || !"DISPONIVEL".equals(result.status()) || result.execucaoPlanejamento() == null
            || result.atendimentos() == null || result.topComidas() == null
            || result.aceitacaoItens() == null || result.aceitacaoItensSemana() == null
            || result.porTurma() == null
            || result.avisos() == null
            || result.aceitacao() == null || result.desperdicio() == null
            || result.ingredientes() == null || !input.dataReferencia().equals(result.dataReferencia())
            || input.turno() != result.turno()) {
            throw new IllegalStateException("Resposta inválida do serviço de indicadores");
        }
        return result;
    }
}
