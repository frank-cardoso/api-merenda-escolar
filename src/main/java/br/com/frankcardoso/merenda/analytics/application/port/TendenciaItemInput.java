package br.com.frankcardoso.merenda.analytics.application.port;

import java.math.BigDecimal;
import java.util.List;

public record TendenciaItemInput(String item, List<BigDecimal> taxasExecucao) {
}
