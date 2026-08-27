package br.com.frankcardoso.merenda.inteligencia.infrastructure.fake;

import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaInput;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaOutput;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaPort;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!gemini")
public class FakeAnaliseLogisticaAdapter implements AnaliseLogisticaPort {

    @Override
    public AnaliseLogisticaOutput analisar(AnaliseLogisticaInput input) {
        var aceitacao = classificarAceitacao(input.taxaConsumoPlanejado());
        var risco = classificarRisco(input.taxaConsumoPlanejado());

        return new AnaliseLogisticaOutput(
            "O cardapio apresentou aceitacao %s e risco de desperdicio %s.".formatted(
                aceitacao.toLowerCase(), risco.toLowerCase()),
            aceitacao,
            risco,
            List.of(
                "Foram servidas %d de %d refeicoes planejadas.".formatted(
                    input.consumosAutorizados(), input.quantidadePlanejada()),
                "A sobra estimada foi de %d refeicoes.".formatted(input.sobraEstimada())
            ),
            List.of("Ajustar gradualmente o planejamento usando a media dos ultimos dias."),
            "Analise demonstrativa gerada sem consulta a um modelo externo."
        );
    }

    private String classificarAceitacao(BigDecimal taxa) {
        if (taxa.compareTo(BigDecimal.valueOf(85)) >= 0) return "ALTA";
        if (taxa.compareTo(BigDecimal.valueOf(60)) >= 0) return "MEDIA";
        return "BAIXA";
    }

    private String classificarRisco(BigDecimal taxa) {
        if (taxa.compareTo(BigDecimal.valueOf(85)) >= 0) return "BAIXO";
        if (taxa.compareTo(BigDecimal.valueOf(60)) >= 0) return "MEDIO";
        return "ALTO";
    }
}
