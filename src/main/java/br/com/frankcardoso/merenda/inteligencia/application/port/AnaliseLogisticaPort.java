package br.com.frankcardoso.merenda.inteligencia.application.port;

public interface AnaliseLogisticaPort {

    AnaliseLogisticaOutput analisar(AnaliseLogisticaInput input);

    /**
     * Identifica quem de fato respondeu a ultima chamada a analisar(). Adapters que
     * delegam para outros (ex: fallback entre provedores) devem refletir o provedor
     * que efetivamente atendeu, nao um valor fixo, para nao mascarar qual IA gerou
     * o resultado.
     */
    default String provedor() {
        return "desconhecido";
    }

    default String modelo() {
        return "desconhecido";
    }
}
