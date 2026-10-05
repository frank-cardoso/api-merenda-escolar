package br.com.frankcardoso.merenda.medicao.api;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lancamento da medicao de um item ao fechar o turno.
 *
 * Nao existe campo "desperdicio": quem lanca informa o que observou (preparado, servido, o que
 * sobrou na cuba e o que voltou no prato) e o calculo fica com o codigo.
 *
 * A receita vem por id, nao por nome: nome nao identifica nada de forma estavel.
 */
public record MedicaoSobraRequest(
    @NotNull LocalDate data,
    @NotNull Turno turno,
    @NotNull UUID receitaId,
    @NotNull @PositiveOrZero Integer porcoesPreparadas,
    @NotNull @PositiveOrZero Integer porcoesServidas,
    @NotNull @PositiveOrZero Integer sobraNaoDistribuida,
    @NotNull @PositiveOrZero Integer restoNoPrato
) {
}
