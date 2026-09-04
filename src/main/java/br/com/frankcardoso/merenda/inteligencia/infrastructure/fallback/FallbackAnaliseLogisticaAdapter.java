package br.com.frankcardoso.merenda.inteligencia.infrastructure.fallback;

import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaInput;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaOutput;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Tenta o provedor primario (Gemini); se falhar por qualquer motivo (erro, timeout,
 * indisponibilidade), tenta o secundario (Groq) antes de desistir. Evita que um
 * unico provedor sobrecarregado derrube todos os relatorios.
 *
 * provedor()/modelo() refletem quem de fato respondeu a ultima chamada (via ThreadLocal,
 * seguro porque analisar() e a leitura subsequente de provedor()/modelo() acontecem
 * sempre na mesma thread, dentro do mesmo Callable do RelatorioIAWorker).
 */
@Component
@Primary
@Profile("gemini")
public class FallbackAnaliseLogisticaAdapter implements AnaliseLogisticaPort {

    private static final Logger LOG = LoggerFactory.getLogger(FallbackAnaliseLogisticaAdapter.class);

    private final AnaliseLogisticaPort primario;
    private final AnaliseLogisticaPort secundario;
    private final ThreadLocal<AnaliseLogisticaPort> ultimoAtendente = new ThreadLocal<>();

    public FallbackAnaliseLogisticaAdapter(
        @Qualifier("geminiAnaliseLogisticaAdapter") AnaliseLogisticaPort primario,
        @Qualifier("groqAnaliseLogisticaAdapter") AnaliseLogisticaPort secundario
    ) {
        this.primario = primario;
        this.secundario = secundario;
    }

    @Override
    public AnaliseLogisticaOutput analisar(AnaliseLogisticaInput input) {
        try {
            var resultado = primario.analisar(input);
            ultimoAtendente.set(primario);
            return resultado;
        } catch (Exception exception) {
            LOG.warn("Provedor de IA primario (Gemini) falhou, acionando fallback (Groq): {}",
                exception.getMessage());
            var resultado = secundario.analisar(input);
            ultimoAtendente.set(secundario);
            return resultado;
        }
    }

    @Override
    public String provedor() {
        var atendente = ultimoAtendente.get();
        return atendente != null ? atendente.provedor() : "desconhecido";
    }

    @Override
    public String modelo() {
        var atendente = ultimoAtendente.get();
        return atendente != null ? atendente.modelo() : "desconhecido";
    }
}
