package br.com.frankcardoso.merenda.inteligencia.application.port;

import java.math.BigDecimal;

/**
 * Item do cardapio de hoje com sua taxa historica de execucao (Java) e a tendencia calculada
 * sobre a serie diaria dessa taxa (Python) — visao ja mastigada para o prompt da IA, sem expor a
 * serie dia a dia bruta.
 *
 * {@code tendencia} e {@code null} quando o servico de tendencia esta indisponivel: a analise
 * segue sem essa informacao, em vez de falhar por causa de um insumo secundario.
 */
public record ItemComTendencia(String item, BigDecimal taxaExecucao, String tendencia) {
}
