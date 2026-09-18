package br.com.frankcardoso.merenda.gestao.application;

import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticosInput;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticosInput.ContagemItem;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticosInput.ContagemTurma;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.historico.infrastructure.HistoricoConsumoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndicadoresDadosService {
    private final ConsolidacaoConsumoService consolidacao;
    private final AuditoriaConsumoRepository auditoria;
    private final HistoricoConsumoRepository historico;
    private final BigDecimal meta;

    public IndicadoresDadosService(ConsolidacaoConsumoService consolidacao,
        AuditoriaConsumoRepository auditoria, HistoricoConsumoRepository historico,
        @Value("${merenda.gestao.meta-execucao-percentual:80}") BigDecimal meta) {
        if (meta.signum() < 0 || meta.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Meta deve estar entre 0 e 100");
        }
        this.consolidacao = consolidacao;
        this.auditoria = auditoria;
        this.historico = historico;
        this.meta = meta;
    }

    // A transação termina antes da chamada HTTP ao Python.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public IndicadoresLogisticosInput preparar(LocalDate data, Turno turno) {
        var consumo = consolidacao.consolidar(data, turno);
        var turmas = auditoria.atendimentosPorTurma(data, turno).stream()
            .map(t -> new ContagemTurma(t.getTurma(), t.getConsumos(), t.getAlunos())).toList();
        var inicio = data.minusDays(29);
        var itens = historico.execucoesNaJanela(inicio, data, turno).stream()
            .collect(Collectors.groupingBy(HistoricoConsumoRepository.ExecucaoItem::getItem,
                LinkedHashMap::new, Collectors.toList()))
            .entrySet().stream().map(entry -> new ContagemItem(entry.getKey(),
                entry.getValue().stream().mapToLong(i -> i.getPlanejamentos()).sum(),
                entry.getValue().stream().mapToLong(i -> i.getExecucoes()).sum(),
                (int) entry.getValue().stream().map(i -> i.getEscola()).distinct().count(),
                entry.getValue().stream().map(i -> i.getOrigem()).distinct().sorted().toList()))
            .toList();
        return new IndicadoresLogisticosInput(data, inicio, turno, consumo.quantidadePlanejada(),
            consumo.consumosAutorizados(), turmas.stream().mapToLong(ContagemTurma::alunosUnicos).sum(),
            meta, turmas, itens);
    }
}
