package br.com.frankcardoso.merenda.inteligencia.infrastructure.fake;

import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaInput;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaOutput;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaPort;
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
        var afirmacoes = conclusao == null ? List.<String>of() : conclusao.afirmacoes();
        var limitacoes = conclusao == null ? List.<String>of() : conclusao.limitacoes();

        return new AnaliseLogisticaOutput(
            afirmacoes.isEmpty()
                ? "Não há conclusões determinísticas disponíveis para este período."
                : String.join(" ", afirmacoes),
            conclusao == null ? "NAO_AVALIAVEL" : conclusao.nivelAceitacao(),
            conclusao == null ? "NAO_AVALIAVEL" : conclusao.riscoDesperdicio(),
            List.of(
                "Foram registrados %d consumos para %d refeições planejadas.".formatted(
                    input.consumosAutorizados(), input.quantidadePlanejada()),
                "A diferença entre planejamento e registros é %d; é sobra de planejamento, não "
                    .formatted(input.sobraDePlanejamento()) + "desperdício medido."
            ),
            List.of("Conferir se o atendimento do turno foi encerrado e se o volume preparado foi "
                + "registrado antes de qualquer revisão de quantidades."),
            limitacoes.isEmpty()
                ? "Análise demonstrativa sem modelo externo."
                : "Análise demonstrativa sem modelo externo. " + String.join(" ", limitacoes)
        );
    }
}
