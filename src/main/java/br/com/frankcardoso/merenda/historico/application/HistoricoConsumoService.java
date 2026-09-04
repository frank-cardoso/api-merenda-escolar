package br.com.frankcardoso.merenda.historico.application;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.historico.api.HistoricoConsumoResumo;
import br.com.frankcardoso.merenda.historico.api.HistoricoConsumoResumo.DiaHistorico;
import br.com.frankcardoso.merenda.historico.api.HistoricoConsumoResumo.ItemHistorico;
import br.com.frankcardoso.merenda.historico.infrastructure.HistoricoConsumoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monta o resumo de historico que vai no prompt da IA.
 *
 * O resumo e deliberadamente compacto: o objetivo e dar contexto suficiente para o modelo
 * comparar o dia analisado com o passado, sem inflar o prompt com centenas de linhas.
 */
@Service
public class HistoricoConsumoService {

    private static final int DIAS_NA_SERIE = 20;
    private static final int ITENS_NO_RANKING = 10;
    private static final int JANELA_DE_ITENS_EM_DIAS = 120;
    private static final long MINIMO_OCORRENCIAS_POR_ITEM = 5;

    private final HistoricoConsumoRepository repository;

    public HistoricoConsumoService(HistoricoConsumoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public HistoricoConsumoResumo resumir(LocalDate dataReferencia, Turno turno,
                                          List<String> itensDoCardapio) {
        var dias = repository.resumoDiario(turno, dataReferencia, PageRequest.of(0, DIAS_NA_SERIE))
            .stream()
            .map(dia -> new DiaHistorico(
                dia.getData(),
                dia.getPlanejado(),
                dia.getServido(),
                percentual(dia.getServido(), dia.getPlanejado())))
            .toList();

        if (dias.isEmpty()) {
            return HistoricoConsumoResumo.vazio();
        }

        // Taxa historica dos itens servidos hoje. E o recorte que permite relacionar o cardapio
        // do dia com o padrao passado; o ranking global abaixo nao da conta disso, porque um
        // item de execucao baixa pode estar fora dos dez piores e ainda explicar o dia.
        var doCardapio = itensDoCardapio.isEmpty()
            ? List.<ItemHistorico>of()
            : repository.taxaDosItens(turno, dataReferencia, itensDoCardapio).stream()
                .map(this::paraItem)
                .toList();

        var itens = repository.taxaPorItem(
                turno,
                dataReferencia.minusDays(JANELA_DE_ITENS_EM_DIAS),
                dataReferencia,
                MINIMO_OCORRENCIAS_POR_ITEM,
                PageRequest.of(0, ITENS_NO_RANKING))
            .stream()
            .map(this::paraItem)
            .toList();

        return new HistoricoConsumoResumo(dias, itens, doCardapio);
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
