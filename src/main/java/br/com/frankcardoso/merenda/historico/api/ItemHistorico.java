package br.com.frankcardoso.merenda.historico.api;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code item} e rotulo de exibicao; o vinculo e {@code receitaId}. */
public record ItemHistorico(UUID receitaId, String item, long vezesPlanejado, long vezesServido,
                            BigDecimal taxaExecucao) {
}
