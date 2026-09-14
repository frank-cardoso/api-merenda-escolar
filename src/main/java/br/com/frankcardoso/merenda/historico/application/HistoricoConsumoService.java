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
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monta os recortes de historico que alimentam a analise de IA.
 *
 * Deliberadamente compacto: o objetivo e dar contexto suficiente para o modelo comparar o dia
 * analisado com o passado, sem inflar o prompt com centenas de linhas.
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
     * Ranking global dos itens de pior execucao. E o recorte que permite correlacionar prato com
     * aceitacao, mas nao responde a pergunta util de um cardapio especifico: um item de execucao
     * baixa pode nao estar entre os dez piores e ainda assim explicar o consumo de hoje — para
     * isso existe {@link #itensDoCardapioComTaxa}.
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

    /**
     * Taxa historica de execucao dos itens do cardapio do dia.
     *
     * O casamento e feito em Java, nao em SQL, porque o nome cadastrado no cardapio nem sempre
     * bate exatamente com o nome do legado ("arroz" cadastrado a mao vs "Arroz branco" do
     * historico) — string igual ignorando caixa/acento/espaco, ou contida uma na outra, e o
     * suficiente para os casos observados; nomes sem nenhuma relacao textual (ex.: "peixe" quando
     * nao ha prato de peixe no historico) continuam sem correspondencia, corretamente.
     */
    @Transactional(readOnly = true)
    public List<ItemHistorico> itensDoCardapioComTaxa(LocalDate dataReferencia, Turno turno,
                                                       List<String> itensDoCardapio) {
        if (itensDoCardapio.isEmpty()) return List.of();

        return repository.taxaPorTodosOsItens(turno, dataReferencia).stream()
            .filter(agregado -> correspondeAoCardapio(agregado.getItem(), itensDoCardapio))
            .map(this::paraItem)
            .toList();
    }

    /**
     * Serie diaria de taxa de execucao dos itens ja resolvidos (nomes reais do historico, nao os
     * do cardapio) — usada pelo calculo de tendencia no servico Python.
     */
    @Transactional(readOnly = true)
    public List<SerieItemHistorico> serieDiariaDosItens(LocalDate dataReferencia, Turno turno,
                                                        List<String> itensResolvidos) {
        if (itensResolvidos.isEmpty()) return List.of();

        return repository.serieDiariaPorItem(turno, dataReferencia, itensResolvidos).stream()
            .collect(Collectors.groupingBy(
                HistoricoConsumoRepository.ItemDiaAgregado::getItem,
                LinkedHashMap::new,
                Collectors.toList()))
            .entrySet().stream()
            .map(entrada -> new SerieItemHistorico(
                entrada.getKey(),
                entrada.getValue().stream()
                    .map(dia -> percentual(dia.getServido(), dia.getPlanejado()))
                    .toList()))
            .toList();
    }

    private boolean correspondeAoCardapio(String itemHistorico, List<String> itensDoCardapio) {
        String normalizado = normalizar(itemHistorico);
        return itensDoCardapio.stream()
            .map(this::normalizar)
            .anyMatch(itemCardapio -> normalizado.equals(itemCardapio)
                || normalizado.contains(itemCardapio)
                || itemCardapio.contains(normalizado));
    }

    private String normalizar(String texto) {
        return StringUtils.stripAccents(texto).trim().toLowerCase(Locale.ROOT);
    }

    private ItemHistorico paraItem(HistoricoConsumoRepository.ItemAgregado agregado) {
        return new ItemHistorico(
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
