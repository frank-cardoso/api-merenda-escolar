package br.com.frankcardoso.merenda.analytics.infrastructure.python;

import br.com.frankcardoso.merenda.analytics.application.port.TendenciaItemInput;
import br.com.frankcardoso.merenda.analytics.application.port.TendenciaItemOutput;
import br.com.frankcardoso.merenda.analytics.application.port.TendenciaItemPort;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PythonTendenciaItemAdapter implements TendenciaItemPort {

    private static final String TENDENCIAS_ITEM_PATH = "/api/v1/tendencias-item";

    private final RestClient restClient;

    @Autowired
    public PythonTendenciaItemAdapter(
        RestClient.Builder builder,
        @Value("${merenda.analytics.base-url:http://localhost:8000}") String baseUrl,
        @Value("${merenda.analytics.timeout:2s}") Duration timeout
    ) {
        this(builder
            .baseUrl(baseUrl)
            .requestFactory(requestFactory(timeout))
            .build());
    }

    PythonTendenciaItemAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<TendenciaItemOutput> calcular(List<TendenciaItemInput> itens) {
        if (itens.isEmpty()) return List.of();

        var output = restClient.post()
            .uri(TENDENCIAS_ITEM_PATH)
            .body(new PythonTendenciaItemRequest(itens.stream()
                .map(SerieItemConsumo::from)
                .toList()))
            .retrieve()
            .body(PythonTendenciaItemResponse.class);

        return Objects.requireNonNull(output, "Servico Python retornou tendencias vazias").tendencias();
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }

    private record SerieItemConsumo(String item, List<BigDecimal> taxasExecucao) {
        private static SerieItemConsumo from(TendenciaItemInput input) {
            return new SerieItemConsumo(input.item(), input.taxasExecucao());
        }
    }

    private record PythonTendenciaItemRequest(List<SerieItemConsumo> itens) {
    }

    private record PythonTendenciaItemResponse(List<TendenciaItemOutput> tendencias) {
    }
}
