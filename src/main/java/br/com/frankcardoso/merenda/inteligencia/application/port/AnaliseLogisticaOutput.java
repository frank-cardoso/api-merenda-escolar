package br.com.frankcardoso.merenda.inteligencia.application.port;

import java.util.List;

public record AnaliseLogisticaOutput(
    String resumoExecutivo,
    String nivelAceitacao,
    String riscoDesperdicio,
    List<String> evidencias,
    List<String> recomendacoes,
    String observacaoLimitacoes
) {
    /**
     * Só o resumo vem do modelo; classificações e limitações são do código e o worker as grava por
     * cima, então o resto do que o modelo devolver não invalida a análise.
     */
    public void validar() {
        if (resumoExecutivo == null || resumoExecutivo.isBlank()) {
            throw new IllegalStateException("A IA retornou uma análise sem resumo");
        }
    }
}
