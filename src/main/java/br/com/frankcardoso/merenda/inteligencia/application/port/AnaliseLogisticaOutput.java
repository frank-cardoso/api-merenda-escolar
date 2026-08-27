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
}
