package br.com.frankcardoso.merenda.historico.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Serie diaria de taxa de execucao de um item, do dia mais antigo para o mais recente.
 *
 * Alimenta o calculo de tendencia no servico Python: taxaPorTodosOsItens da o dado agregado de
 * um item na janela toda, mas nao diz se a aceitacao esta subindo ou caindo ao longo do tempo.
 */
public record SerieItemHistorico(UUID receitaId, String item, List<BigDecimal> taxasExecucao) {
}
