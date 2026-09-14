package br.com.frankcardoso.merenda.historico.api;

import java.math.BigDecimal;

public record ItemHistorico(String item, long vezesPlanejado, long vezesServido,
                            BigDecimal taxaExecucao) {
}
