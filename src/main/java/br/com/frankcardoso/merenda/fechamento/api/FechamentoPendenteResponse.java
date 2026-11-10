package br.com.frankcardoso.merenda.fechamento.api;

import java.time.LocalDate;
import java.util.UUID;

public record FechamentoPendenteResponse(
    LocalDate data,
    UUID tipoRefeicaoId,
    String refeicao,
    int itensCardapio,
    int itensMedidos
) {
}
