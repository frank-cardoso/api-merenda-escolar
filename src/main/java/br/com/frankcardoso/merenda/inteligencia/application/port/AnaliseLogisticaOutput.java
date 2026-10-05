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
    public void validar() {
        if (resumoExecutivo == null || resumoExecutivo.isBlank()
            || nivelAceitacao == null || !List.of("ALTA", "MEDIA", "BAIXA", "NAO_AVALIAVEL").contains(nivelAceitacao)
            || riscoDesperdicio == null || !List.of("ALTO", "MEDIO", "BAIXO", "NAO_AVALIAVEL").contains(riscoDesperdicio)
            || evidencias == null || recomendacoes == null
            || evidencias.stream().anyMatch(s -> s == null || s.isBlank())
            || recomendacoes.stream().anyMatch(s -> s == null || s.isBlank())
            || observacaoLimitacoes == null || observacaoLimitacoes.isBlank()) {
            throw new IllegalStateException("A IA retornou uma análise com campos inválidos");
        }
    }
}
