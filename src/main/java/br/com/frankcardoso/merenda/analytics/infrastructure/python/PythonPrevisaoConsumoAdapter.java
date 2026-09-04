package br.com.frankcardoso.merenda.analytics.infrastructure.python;

import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoInput;
import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoOutput;
import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoPort;
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
public class PythonPrevisaoConsumoAdapter implements PrevisaoConsumoPort {

    private static final String PREVISOES_CONSUMO_PATH = "/api/v1/previsoes-consumo";

    private final RestClient restClient;

    @Autowired
    public PythonPrevisaoConsumoAdapter(
        RestClient.Builder builder,
        @Value("${merenda.analytics.base-url:http://localhost:8000}") String baseUrl,
        @Value("${merenda.analytics.timeout:2s}") Duration timeout
    ) {
        this(builder
            .baseUrl(baseUrl)
            .requestFactory(requestFactory(timeout))
            .build());
    }

    PythonPrevisaoConsumoAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public PrevisaoConsumoOutput prever(PrevisaoConsumoInput input) {
        var output = restClient.post()
            .uri(PREVISOES_CONSUMO_PATH)
            .body(PythonPrevisaoConsumoRequest.from(input))
            .retrieve()
            .body(PrevisaoConsumoOutput.class);

        return Objects.requireNonNull(output, "Servico Python retornou previsao vazia");
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }

    private record DiaHistorico(String dataReferencia, long consumosAutorizados) {
    }

    private record PythonPrevisaoConsumoRequest(
        String dataReferencia,
        String turno,
        String cardapio,
        int quantidadePlanejada,
        long consumosAutorizados,
        long tentativasBloqueadas,
        BigDecimal taxaConsumoPlanejado,
        long sobraEstimada,
        List<DiaHistorico> historico
    ) {

        private static PythonPrevisaoConsumoRequest from(PrevisaoConsumoInput input) {
            return new PythonPrevisaoConsumoRequest(
                input.dataReferencia().toString(),
                input.turno().name(),
                input.cardapio(),
                input.quantidadePlanejada(),
                input.consumosAutorizados(),
                input.tentativasBloqueadas(),
                input.taxaConsumoPlanejado(),
                input.sobraEstimada(),
                input.historico().stream()
                    .map(dia -> new DiaHistorico(
                        dia.dataReferencia().toString(), dia.consumosAutorizados()))
                    .toList()
            );
        }
    }
}
