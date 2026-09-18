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
    public String provedor() {
        return "fake";
    }

    @Override
    public String modelo() {
        return "regras-locais-v1";
    }

    @Override
    public AnaliseLogisticaOutput analisar(AnaliseLogisticaInput input) {
        var risco = classificarRisco(input.taxaConsumoPlanejado());
        var execucao = input.indicadores() == null ? null : input.indicadores().execucaoPlanejamento();
        var resumo = execucao == null || execucao.percentual() == null
            ? "Não foi possível avaliar a meta de execução com os indicadores disponíveis."
            : "Execução registrada de %s%% para a meta interna de %s%%: %s.".formatted(
                execucao.percentual(), execucao.metaPercentual(), execucao.statusMeta());

        return new AnaliseLogisticaOutput(
            resumo,
            "NAO_AVALIAVEL",
            input.quantidadePlanejada() == 0 ? "NAO_AVALIAVEL" : risco,
            List.of(
                "Foram registrados %d consumos para %d refeições planejadas.".formatted(
                    input.consumosAutorizados(), input.quantidadePlanejada()),
                "A diferença positiva entre planejamento e registros é %d; não é desperdício medido."
                    .formatted(input.sobraEstimada())
            ),
            List.of("Ajustar gradualmente o planejamento usando a media dos ultimos dias."),
            "Análise demonstrativa sem modelo externo. QR Code não mede aceitação; sem presença "
                + "não se avalia adesão por turma. Ingredientes e rotação exigem dados adicionais."
        );
    }

    private String classificarRisco(BigDecimal taxa) {
        if (taxa.compareTo(BigDecimal.valueOf(85)) >= 0) return "BAIXO";
        if (taxa.compareTo(BigDecimal.valueOf(60)) >= 0) return "MEDIO";
        return "ALTO";
    }
}
