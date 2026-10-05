package br.com.frankcardoso.merenda.gestao.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CardapioAnaliseResponse(
    String chave,
    String nome,
    List<String> itens,
    List<UUID> receitaIds,
    List<LocalDate> datasServido,
    List<LocalDate> datasComFechamento,
    int ocorrencias,
    boolean analisavel,
    String motivo
) {
}
