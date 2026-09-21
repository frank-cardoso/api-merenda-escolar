package br.com.frankcardoso.merenda.gestao.application;

import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticosInput;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticosInput.ContagemItem;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticosInput.ContagemTurma;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticosInput.IngredienteRef;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticosInput.MedicaoDoDia;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticosInput.ReceitaMedida;
import br.com.frankcardoso.merenda.catalogo.infrastructure.ReceitaIngredienteRepository;
import br.com.frankcardoso.merenda.medicao.infrastructure.MedicaoSobraRepository;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.historico.infrastructure.HistoricoConsumoRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final MedicaoSobraRepository medicoes;
    private final ReceitaIngredienteRepository composicao;
    private final BigDecimal meta;
    private final int escolaDoPrototipo;
    private final int diasJanelaIngredientes;

    public IndicadoresDadosService(ConsolidacaoConsumoService consolidacao,
        AuditoriaConsumoRepository auditoria, HistoricoConsumoRepository historico,
        MedicaoSobraRepository medicoes, ReceitaIngredienteRepository composicao,
        @Value("${merenda.gestao.meta-execucao-percentual:80}") BigDecimal meta,
        @Value("${merenda.medicao.escola-id:1}") int escolaDoPrototipo,
        @Value("${merenda.medicao.dias-janela-ingredientes:180}") int diasJanelaIngredientes) {
        if (meta.signum() < 0 || meta.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Meta deve estar entre 0 e 100");
        }
        this.consolidacao = consolidacao;
        this.auditoria = auditoria;
        this.historico = historico;
        this.medicoes = medicoes;
        this.composicao = composicao;
        this.meta = meta;
        this.escolaDoPrototipo = escolaDoPrototipo;
        this.diasJanelaIngredientes = diasJanelaIngredientes;
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
            meta, turmas, itens, medicaoDoDia(data, turno, consumo.receitasCardapio()),
            receitasMedidas(data, turno));
    }

    private MedicaoDoDia medicaoDoDia(LocalDate data, Turno turno, List<UUID> receitasDoCardapio) {
        if (receitasDoCardapio == null || receitasDoCardapio.isEmpty()) return null;

        var resumo = medicoes.resumoDoDia(data, turno, escolaDoPrototipo, receitasDoCardapio);
        // Nenhum item medido significa que ninguem lancou a sobra do turno, nao que ela foi zero.
        if (resumo == null || resumo.getItens() == 0) return null;
        return new MedicaoDoDia(resumo.getPreparadas(), resumo.getServidas(), resumo.getSobra(),
            resumo.getResto(), (int) resumo.getItens(), receitasDoCardapio.size());
    }

    /**
     * Receitas medidas na janela com a composicao de cada uma.
     *
     * Janela propria, mais longa que a dos indicadores: preferencia por ingrediente muda devagar.
     * O grafo vai no payload para o servico Python nao precisar de tabela de ingrediente nenhuma.
     */
    private List<ReceitaMedida> receitasMedidas(LocalDate data, Turno turno) {
        var medidas = medicoes.restoPorReceita(
            data.minusDays(diasJanelaIngredientes), data, turno);
        if (medidas.isEmpty()) return List.of();

        var receitas = medidas.stream().map(MedicaoSobraRepository.RestoReceita::getReceitaId).toList();
        Map<UUID, List<IngredienteRef>> porReceita = composicao.grafoDasReceitas(receitas).stream()
            .collect(Collectors.groupingBy(
                ReceitaIngredienteRepository.VinculoIngrediente::getReceitaId,
                LinkedHashMap::new,
                Collectors.mapping(vinculo -> new IngredienteRef(
                    vinculo.getIngredienteId(), vinculo.getIngredienteNome()), Collectors.toList())));

        return medidas.stream()
            .map(medida -> new ReceitaMedida(
                medida.getReceitaId(),
                medida.getNome(),
                porReceita.getOrDefault(medida.getReceitaId(), List.of()),
                medida.getServidas(),
                medida.getResto(),
                medida.getAmostra()))
            .toList();
    }
}
