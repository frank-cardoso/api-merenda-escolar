package br.com.frankcardoso.merenda.relatorio.application;

import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoInput;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos;
import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoOutput;
import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoPort;
import br.com.frankcardoso.merenda.analytics.application.port.RefeicaoRef;
import br.com.frankcardoso.merenda.analytics.application.port.TendenciaItemInput;
import br.com.frankcardoso.merenda.analytics.application.port.TendenciaItemOutput;
import br.com.frankcardoso.merenda.analytics.application.port.TendenciaItemPort;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.gestao.api.ConsolidacaoConsumoResponse;
import br.com.frankcardoso.merenda.gestao.application.ConsolidacaoConsumoService;
import br.com.frankcardoso.merenda.gestao.application.IndicadoresLogisticosService;
import br.com.frankcardoso.merenda.historico.api.ItemHistorico;
import br.com.frankcardoso.merenda.historico.application.HistoricoConsumoService;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaInput;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaOutput;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaPort;
import br.com.frankcardoso.merenda.inteligencia.application.port.ConclusaoDeterministica;
import br.com.frankcardoso.merenda.inteligencia.application.port.IndicadoresParaLLM;
import br.com.frankcardoso.merenda.inteligencia.application.port.ItemComTendencia;
import br.com.frankcardoso.merenda.relatorio.domain.RelatorioIA;
import br.com.frankcardoso.merenda.relatorio.infrastructure.RelatorioIARepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class RelatorioIAWorker {

    private static final Logger LOG = LoggerFactory.getLogger(RelatorioIAWorker.class);

    private final RelatorioIARepository repository;
    private final ConsolidacaoConsumoService consolidacaoService;
    private final AnaliseLogisticaPort analisePort;
    private final PrevisaoConsumoPort previsaoPort;
    private final TendenciaItemPort tendenciaItemPort;
    private final HistoricoConsumoService historicoService;
    private final AuditoriaConsumoRepository auditoriaRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String provedor;
    private final String modelo;
    private final Duration timeoutAnaliseIA;
    private final IndicadoresLogisticosService indicadoresService;

    /** Vinte dias uteis cobrem cerca de um mes e ja dao confianca ALTA na previsao. */
    private static final int DIAS_DE_HISTORICO_NA_PREVISAO = 20;

    public RelatorioIAWorker(
        RelatorioIARepository repository,
        ConsolidacaoConsumoService consolidacaoService,
        AnaliseLogisticaPort analisePort,
        PrevisaoConsumoPort previsaoPort,
        TendenciaItemPort tendenciaItemPort,
        HistoricoConsumoService historicoService,
        AuditoriaConsumoRepository auditoriaRepository,
        ObjectMapper objectMapper,
        Clock clock,
        @Value("${merenda.ia.provedor:fake}") String provedor,
        @Value("${merenda.ia.modelo:regras-locais-v1}") String modelo,
        @Value("${merenda.ia.timeout-segundos:30}") long timeoutSegundos,
        IndicadoresLogisticosService indicadoresService
    ) {
        this.repository = repository;
        this.consolidacaoService = consolidacaoService;
        this.analisePort = analisePort;
        this.previsaoPort = previsaoPort;
        this.tendenciaItemPort = tendenciaItemPort;
        this.historicoService = historicoService;
        this.auditoriaRepository = auditoriaRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.provedor = provedor;
        this.modelo = modelo;
        this.timeoutAnaliseIA = Duration.ofSeconds(timeoutSegundos);
        this.indicadoresService = indicadoresService;
    }

    @Async("relatorioExecutor")
    public void processar(UUID relatorioId) {
        RelatorioIA relatorio = repository.findById(relatorioId)
            .orElseThrow(() -> new RelatorioNaoEncontradoException(relatorioId));

        try {
            var selecionadas = receitasSelecionadas(relatorio);
            var datasSelecionadas = datasSelecionadas(relatorio);
            ConsolidacaoConsumoResponse consolidacao = consolidarComReferencia(
                relatorio.getDataReferencia(), relatorio.getTipoRefeicaoId(), datasSelecionadas);
            var indicadores = selecionadas.isEmpty() && datasSelecionadas.isEmpty()
                ? indicadoresService.calcular(consolidacao.data(), consolidacao.tipoRefeicaoId())
                : indicadoresService.calcular(consolidacao.data(), consolidacao.tipoRefeicaoId(), selecionadas,
                    datasSelecionadas);
            if (indicadores == null) {
                indicadores = IndicadoresLogisticos.indisponivel(consolidacao.data(),
                    new RefeicaoRef(consolidacao.tipoRefeicaoId(), consolidacao.refeicao()));
            }
            consolidacao = alinharConsolidacaoAFotografia(consolidacao, indicadores);
            AnaliseLogisticaInput input = criarInput(consolidacao, preverConsumo(consolidacao), indicadores,
                selecionadas, relatorio.getTurmaId());

            relatorio.registrarIndicadores(escreverJson(indicadores));

            relatorio.iniciar(provedor, modelo, escreverJson(input), Instant.now(clock));
            relatorio = repository.saveAndFlush(relatorio);

            var resultado = analisarOuResumirSemIA(input);
            var saida = comClassificacoesDeterministicas(
                resultado.saida(), input.conclusaoDeterministica());
            // A classificacao e responsabilidade do codigo. O modelo pode devolver um valor
            // textual fora do enum, mas isso nao deve invalidar um relatorio cuja conclusao
            // deterministica ja tem os dois niveis corretos.
            saida.validar();

            relatorio.concluir(resultado.provedor(), resultado.modelo(), escreverJson(saida),
                saida.resumoExecutivo(), Instant.now(clock));
            repository.save(relatorio);
        } catch (Exception exception) {
            relatorio.falhar(mensagemSegura(exception), Instant.now(clock));
            repository.save(relatorio);
        }
    }

    private ConsolidacaoConsumoResponse consolidarComReferencia(
        LocalDate dataReferencia, UUID tipoRefeicaoId, List<LocalDate> datasSelecionadas) {
        try {
            return consolidacaoService.consolidar(dataReferencia, tipoRefeicaoId);
        } catch (RuntimeException excecao) {
            if (datasSelecionadas.isEmpty()) throw excecao;

            var dataComCardapio = datasSelecionadas.stream().max(LocalDate::compareTo)
                .orElseThrow();
            var escopo = consolidacaoService.consolidar(dataComCardapio, tipoRefeicaoId);
            // A data de referência continua sendo a data escolhida no dashboard; somente a
            // fotografia operacional vem do último dia em que o cardápio selecionado existiu.
            return new ConsolidacaoConsumoResponse(
                dataReferencia, escopo.tipoRefeicaoId(), escopo.refeicao(), escopo.cardapioId(), escopo.cardapio(),
                escopo.itensCardapio(), escopo.receitasCardapio(), escopo.quantidadePlanejada(),
                escopo.consumosAutorizados(), escopo.tentativasBloqueadas(),
                escopo.taxaConsumoPlanejado(), escopo.sobraEstimada());
        }
    }

    private List<UUID> receitasSelecionadas(RelatorioIA relatorio) {
        var json = relatorio.getReceitaIdsJson();
        if (json == null || json.isBlank()) return List.of();
        return Arrays.stream(json.split(",")).map(UUID::fromString).toList();
    }

    private List<LocalDate> datasSelecionadas(RelatorioIA relatorio) {
        var json = relatorio.getDatasSelecionadasJson();
        if (json == null || json.isBlank()) return List.of();
        return Arrays.stream(json.split(",")).map(LocalDate::parse).toList();
    }

    private AnaliseLogisticaInput criarInput(
        ConsolidacaoConsumoResponse consolidacao,
        ResultadoPrevisao resultadoPrevisao,
        IndicadoresLogisticos indicadores,
        List<UUID> receitasSelecionadas,
        UUID turmaId
    ) {
        var ranking = historicoService.rankingDosPioresItens(consolidacao.data(), consolidacao.tipoRefeicaoId());
        var escopoSelecionado = !receitasSelecionadas.isEmpty();
        var receitasDoEscopo = escopoSelecionado
            ? receitasSelecionadas
            : consolidacao.receitasCardapio();
        var itensComTaxa = historicoService.itensDoCardapioComTaxa(
            consolidacao.data(), consolidacao.tipoRefeicaoId(), receitasDoEscopo);
        var itensDoEscopo = escopoSelecionado && indicadores.aceitacaoItens() != null
            ? indicadores.aceitacaoItens().stream()
                .map(IndicadoresLogisticos.AceitacaoItem::item).toList()
            : consolidacao.itensCardapio();
        var nomeDoEscopo = escopoSelecionado
            ? "Cardápio selecionado: " + String.join(", ", itensDoEscopo)
            : consolidacao.cardapio();

        return new AnaliseLogisticaInput(
            consolidacao.data(),
            consolidacao.refeicao(),
            nomeDoEscopo,
            itensDoEscopo,
            indicadores.aceitacaoItens() == null ? List.of() : indicadores.aceitacaoItens().stream()
                .map(IndicadoresLogisticos.AceitacaoItem::item).toList(),
            ranking,
            itensComTendencia(consolidacao, itensComTaxa),
            IndicadoresParaLLM.de(indicadores, turmaId),
            ConclusaoDeterministica.de(indicadores, resultadoPrevisao.previsao())
        );
    }

    /**
     * O modelo redige só o resumo, o codigo classifica e escreve as limitacoes. Sem isso o painel
     * voltava a estampar desperdicio ALTO sem nenhuma medicao de sobra por tras — o modelo apenas
     * repetia o campo que ja chegava classificado no payload.
     */
    private AnaliseLogisticaOutput comClassificacoesDeterministicas(
        AnaliseLogisticaOutput saida, ConclusaoDeterministica conclusao
    ) {
        if (conclusao == null) return saida;
        return new AnaliseLogisticaOutput(saida.resumoExecutivo(), conclusao.nivelAceitacao(),
            conclusao.riscoDesperdicio(), List.of(), List.of(),
            String.join(" ", conclusao.limitacoes()));
    }

    // A fila pode avançar enquanto as consultas são feitas. A fotografia prevalece também
    // nos campos legados enviados à previsão/LLM, para não enviar duas contagens diferentes.
    private ConsolidacaoConsumoResponse alinharConsolidacaoAFotografia(
        ConsolidacaoConsumoResponse original, IndicadoresLogisticos indicadores) {
        if (indicadores == null || indicadores.execucaoPlanejamento() == null) return original;
        var execucao = indicadores.execucaoPlanejamento();
        return new ConsolidacaoConsumoResponse(original.data(), original.tipoRefeicaoId(), original.refeicao(),
            original.cardapioId(), original.cardapio(), original.itensCardapio(), original.receitasCardapio(),
            Math.toIntExact(execucao.refeicoesPlanejadas()),
            execucao.consumosRegistrados(), original.tentativasBloqueadas(),
            execucao.percentual() == null ? BigDecimal.ZERO : execucao.percentual(),
            Math.max(0, execucao.refeicoesPlanejadas() - execucao.consumosRegistrados()));
    }

    /**
     * Mescla a taxa historica de cada item do cardapio de hoje (Java) com a tendencia calculada
     * sobre a serie diaria dessa taxa (Python). Se o servico de tendencia falhar, a analise segue
     * sem tendencia em vez de falhar por causa de um insumo secundario — mesmo principio do
     * preverConsumo() para a previsao de demanda.
     */
    private List<ItemComTendencia> itensComTendencia(
        ConsolidacaoConsumoResponse consolidacao,
        List<ItemHistorico> itensComTaxa
    ) {
        if (itensComTaxa.isEmpty()) return List.of();

        Map<String, String> tendenciaPorReceita;
        try {
            var receitas = itensComTaxa.stream().map(ItemHistorico::receitaId).toList();
            var series = historicoService.serieDiariaDosItens(
                consolidacao.data(), consolidacao.tipoRefeicaoId(), receitas);

            // A chave trafegada e o id da receita: o servico de tendencia so devolve a chave que
            // recebeu, e nome nao serve de chave estavel.
            tendenciaPorReceita = tendenciaItemPort.calcular(
                    series.stream()
                        .map(serie -> new TendenciaItemInput(
                            serie.receitaId().toString(), serie.taxasExecucao()))
                        .toList())
                .stream()
                .collect(Collectors.toMap(TendenciaItemOutput::item, TendenciaItemOutput::tendencia));
        } catch (Exception exception) {
            LOG.warn("Servico de tendencia de itens falhou, seguindo sem tendencia: {}",
                exception.getMessage());
            tendenciaPorReceita = Map.of();
        }

        Map<String, String> tendenciaResolvida = tendenciaPorReceita;
        return itensComTaxa.stream()
            .map(item -> new ItemComTendencia(item.receitaId(), item.item(), item.taxaExecucao(),
                tendenciaResolvida.get(item.receitaId().toString())))
            .toList();
    }

    private ResultadoPrevisao preverConsumo(ConsolidacaoConsumoResponse consolidacao) {
        try {
            var previsao = previsaoPort.prever(new PrevisaoConsumoInput(
                consolidacao.data(),
                consolidacao.refeicao(),
                consolidacao.cardapio(),
                consolidacao.quantidadePlanejada(),
                consolidacao.consumosAutorizados(),
                consolidacao.tentativasBloqueadas(),
                consolidacao.taxaConsumoPlanejado(),
                consolidacao.sobraEstimada(),
                consumosDosDiasAnteriores(consolidacao)
            ));

            return new ResultadoPrevisao(previsao, null);
        } catch (Exception exception) {
            return new ResultadoPrevisao(null, mensagemSegura(exception));
        }
    }

    /**
     * Serie de consumo dos dias anteriores na mesma refeicao, para a previsao deixar de ser
     * circular: sem ela o servico devolve o consumo do proprio dia como estimativa.
     */
    private List<PrevisaoConsumoInput.DiaConsumo> consumosDosDiasAnteriores(
        ConsolidacaoConsumoResponse consolidacao
    ) {
        return auditoriaRepository.consumosPorDia(
                consolidacao.tipoRefeicaoId(),
                consolidacao.data(),
                PageRequest.of(0, DIAS_DE_HISTORICO_NA_PREVISAO))
            .stream()
            .map(dia -> new PrevisaoConsumoInput.DiaConsumo(dia.getData(), dia.getAutorizados()))
            .toList();
    }

    private record ResultadoPrevisao(
        PrevisaoConsumoOutput previsao,
        String aviso
    ) {
    }

    private record ResultadoAnalise(AnaliseLogisticaOutput saida, String provedor, String modelo) {
    }

    // Tudo o que o modelo escreve e o resumo; sem ele o relatorio sai com o resumo montado pelo codigo.
    private ResultadoAnalise analisarOuResumirSemIA(AnaliseLogisticaInput input) {
        try {
            var resultado = analisarComTimeout(input);
            if (resultado.saida() == null) {
                throw new IllegalStateException("O provedor de IA retornou resposta vazia");
            }
            return resultado;
        } catch (RuntimeException exception) {
            LOG.warn("Relatorio concluido sem IA, com resumo deterministico: {}", mensagemSegura(exception));
            var saida = new AnaliseLogisticaOutput(
                ConclusaoDeterministica.resumoCurto(input.indicadores()), null, null,
                List.of(), List.of(), null);
            return new ResultadoAnalise(saida, "deterministico", null);
        }
    }

    private ResultadoAnalise analisarComTimeout(AnaliseLogisticaInput input) {
        CompletableFuture<ResultadoAnalise> analise = CompletableFuture.supplyAsync(() -> {
            var saida = analisePort.analisar(input);
            return new ResultadoAnalise(saida, analisePort.provedor(), analisePort.modelo());
        });

        try {
            return analise.get(timeoutAnaliseIA.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new IllegalStateException(
                "O provedor de IA nao respondeu em " + timeoutAnaliseIA.toSeconds() + "s");
        } catch (ExecutionException exception) {
            Throwable causa = exception.getCause();
            if (causa instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException(
                causa != null ? causa.getMessage() : exception.getMessage(), causa);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Processamento da analise de IA foi interrompido", exception);
        }
    }

    private String escreverJson(Object valor) throws JsonProcessingException {
        return objectMapper.writeValueAsString(valor);
    }

    private String mensagemSegura(Exception exception) {
        Throwable maisEspecifica = causaRaiz(exception);
        String mensagem = maisEspecifica.getMessage();
        if (mensagem == null || mensagem.isBlank()) return maisEspecifica.getClass().getSimpleName();
        return mensagem.length() > 500 ? mensagem.substring(0, 500) : mensagem;
    }

    private Throwable causaRaiz(Throwable exception) {
        Throwable maisProfundaComMensagemUtil = exception;
        Throwable atual = exception;
        while (atual.getCause() != null && atual.getCause() != atual) {
            atual = atual.getCause();
            if (temMensagemUtil(atual)) maisProfundaComMensagemUtil = atual;
        }
        return maisProfundaComMensagemUtil;
    }

    private boolean temMensagemUtil(Throwable exception) {
        String mensagem = exception.getMessage();
        return mensagem != null && !mensagem.isBlank() && !"Canceled".equalsIgnoreCase(mensagem);
    }
}
