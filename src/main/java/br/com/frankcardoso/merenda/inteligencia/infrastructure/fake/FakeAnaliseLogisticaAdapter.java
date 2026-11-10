package br.com.frankcardoso.merenda.inteligencia.infrastructure.fake;

import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaInput;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaOutput;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaPort;
import br.com.frankcardoso.merenda.inteligencia.application.port.ConclusaoDeterministica;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Espelha o comportamento esperado do provedor real: redige a partir de
 * {@code conclusaoDeterministica} e nao classifica nada por conta propria.
 */
@Component
@Profile("!gemini")
public class FakeAnaliseLogisticaAdapter implements AnaliseLogisticaPort {

    @Override
    public String provedor() {
        return "fake";
    }

    @Override
    public String modelo() {
        return "regras-locais-v1";
    }

    @Override
    public AnaliseLogisticaOutput analisar(AnaliseLogisticaInput input) {
        var conclusao = input.conclusaoDeterministica();
        var limitacoes = conclusao == null ? List.<String>of() : conclusao.limitacoes();

        return new AnaliseLogisticaOutput(
            ConclusaoDeterministica.resumoCurto(input.indicadores()),
            conclusao == null ? "NAO_AVALIAVEL" : conclusao.nivelAceitacao(),
            conclusao == null ? "NAO_AVALIAVEL" : conclusao.riscoDesperdicio(),
            List.of(), List.of(),
            String.join(" ", limitacoes)
        );
    }
}
