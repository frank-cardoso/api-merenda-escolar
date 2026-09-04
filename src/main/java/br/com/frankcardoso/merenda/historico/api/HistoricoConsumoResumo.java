package br.com.frankcardoso.merenda.historico.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Recorte de historico enviado no prompt da IA.
 *
 * {@code itensComMenorExecucao} vem ordenado do pior para o melhor: e esse ranking que permite
 * ao modelo apontar correlacao entre prato e execucao, em vez de so descrever o dia analisado.
 */
public record HistoricoConsumoResumo(
    List<DiaHistorico> ultimosDias,
    List<ItemHistorico> itensComMenorExecucao,
    List<ItemHistorico> itensDoCardapioDeHoje
) {

    public static HistoricoConsumoResumo vazio() {
        return new HistoricoConsumoResumo(List.of(), List.of(), List.of());
    }

    public boolean estaVazio() {
        return ultimosDias.isEmpty();
    }

    public record DiaHistorico(
        LocalDate data,
        long quantidadePlanejada,
        long quantidadeServida,
        BigDecimal taxaExecucao
    ) {
    }

    public record ItemHistorico(
        String item,
        long vezesPlanejado,
        long vezesServido,
        BigDecimal taxaExecucao
    ) {
    }
}
