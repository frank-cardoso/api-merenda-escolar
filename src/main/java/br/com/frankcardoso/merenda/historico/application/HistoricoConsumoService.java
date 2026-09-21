package br.com.frankcardoso.merenda.historico.application;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.historico.api.ItemHistorico;
import br.com.frankcardoso.merenda.historico.api.SerieItemHistorico;
import br.com.frankcardoso.merenda.historico.infrastructure.HistoricoConsumoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monta os recortes de historico que alimentam a analise de IA.
 *
 * Deliberadamente compacto: o objetivo e dar contexto suficiente para o modelo comparar o dia
 * analisado com o passado, sem inflar o prompt com centenas de linhas.
 *
 * Todo casamento de item e por id. A versao anterior comparava nome normalizado, com substring nos
 * dois sentidos, porque o cardapio era texto livre — o que fazia "Arroz" casar tanto com "Arroz
 * branco" quanto com "Arroz com galinha". Com o cardapio referenciando receita, o filtro e exato.
 */
@Service
public class HistoricoConsumoService {

    private static final int ITENS_NO_RANKING = 10;
    private static final int JANELA_DE_ITENS_EM_DIAS = 120;
    private static final long MINIMO_OCORRENCIAS_POR_ITEM = 5;

    private final HistoricoConsumoRepository repository;

    public HistoricoConsumoService(HistoricoConsumoRepository repository) {
        this.repository = repository;
    }

    /**
     * Ranking global das receitas de pior execucao. E o recorte que permite correlacionar prato com
     * aceitacao, mas nao responde a pergunta util de um cardapio especifico: uma receita de
     * execucao baixa pode nao estar entre as dez piores e ainda assim explicar o consumo de hoje —
     * para isso existe {@link #itensDoCardapioComTaxa}.
     */
    @Transactional(readOnly = true)
    public List<ItemHistorico> rankingDosPioresItens(LocalDate dataReferencia, Turno turno) {
        return repository.taxaPorItem(
                turno,
                dataReferencia.minusDays(JANELA_DE_ITENS_EM_DIAS),
                dataReferencia,
                MINIMO_OCORRENCIAS_POR_ITEM,
                PageRequest.of(0, ITENS_NO_RANKING))
            .stream()
            .map(this::paraItem)
            .toList();
    }

    /** Taxa historica de execucao das receitas do cardapio do dia. */
    @Transactional(readOnly = true)
    public List<ItemHistorico> itensDoCardapioComTaxa(LocalDate dataReferencia, Turno turno,
                                                       List<UUID> receitasDoCardapio) {
        if (receitasDoCardapio.isEmpty()) return List.of();

        return repository.taxaPorReceitas(turno, dataReferencia, receitasDoCardapio).stream()
            .map(this::paraItem)
            .toList();
    }

    /**
     * Serie diaria de taxa de execucao das receitas informadas — usada pelo calculo de tendencia
     * no servico Python.
     */
    @Transactional(readOnly = true)
    public List<SerieItemHistorico> serieDiariaDosItens(LocalDate dataReferencia, Turno turno,
                                                        List<UUID> receitas) {
        if (receitas.isEmpty()) return List.of();

        return repository.serieDiariaPorReceita(turno, dataReferencia, receitas).stream()
            .collect(Collectors.groupingBy(
                HistoricoConsumoRepository.ItemDiaAgregado::getReceitaId,
                LinkedHashMap::new,
                Collectors.toList()))
            .values().stream()
            .map(dias -> new SerieItemHistorico(
                dias.getFirst().getReceitaId(),
                dias.getFirst().getItem(),
                dias.stream().map(dia -> percentual(dia.getServido(), dia.getPlanejado())).toList()))
            .toList();
    }

    private ItemHistorico paraItem(HistoricoConsumoRepository.ItemAgregado agregado) {
        return new ItemHistorico(
            agregado.getReceitaId(),
            agregado.getItem(),
            agregado.getVezesPlanejado(),
            agregado.getVezesServido(),
            percentual(agregado.getVezesServido(), agregado.getVezesPlanejado()));
    }

    private BigDecimal percentual(long parte, long total) {
        if (total == 0) return BigDecimal.ZERO.setScale(1);
        return BigDecimal.valueOf(parte)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }
}
