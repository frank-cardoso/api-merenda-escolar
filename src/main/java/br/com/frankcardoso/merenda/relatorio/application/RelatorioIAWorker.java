package br.com.frankcardoso.merenda.relatorio.application;

import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoInput;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos;
import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoOutput;
import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoPort;
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
import java.math.BigDecimal;
import java.util.List;
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
            ConsolidacaoConsumoResponse consolidacao = consolidacaoService.consolidar(
                relatorio.getDataReferencia(), relatorio.getTurno());
            var indicadores = indicadoresService.calcular(consolidacao.data(), consolidacao.turno());
            consolidacao = alinharConsolidacaoAFotografia(consolidacao, indicadores);
            AnaliseLogisticaInput input = criarInput(consolidacao, preverConsumo(consolidacao), indicadores);

            relatorio.registrarIndicadores(escreverJson(indicadores));

            relatorio.iniciar(provedor, modelo, escreverJson(input), Instant.now(clock));
            relatorio = repository.saveAndFlush(relatorio);

            var resultado = analisarComTimeout(input);
            if (resultado.saida() == null) {
                throw new IllegalStateException("O provedor de IA retornou resposta vazia");
            }
            resultado.saida().validar();
            var saida = comClassificacoesDeterministicas(
                resultado.saida(), input.conclusaoDeterministica());

            relatorio.concluir(resultado.provedor(), resultado.modelo(), escreverJson(saida),
                saida.resumoExecutivo(), Instant.now(clock));
            repository.save(relatorio);
        } catch (Exception exception) {
            relatorio.falhar(mensagemSegura(exception), Instant.now(clock));
            repository.save(relatorio);
        }
    }

    private AnaliseLogisticaInput criarInput(
        ConsolidacaoConsumoResponse consolidacao,
        ResultadoPrevisao resultadoPrevisao,
        IndicadoresLogisticos indicadores
    ) {
        var ranking = historicoService.rankingDosPioresItens(consolidacao.data(), consolidacao.turno());
        var itensComTaxa = historicoService.itensDoCardapioComTaxa(
            consolidacao.data(), consolidacao.turno(), consolidacao.receitasCardapio());

        return new AnaliseLogisticaInput(
            consolidacao.data(),
            consolidacao.turno(),
            consolidacao.cardapio(),
            consolidacao.itensCardapio(),
            consolidacao.quantidadePlanejada(),
            consolidacao.consumosAutorizados(),
            consolidacao.tentativasBloqueadas(),
            consolidacao.taxaConsumoPlanejado(),
            consolidacao.sobraEstimada(),
            resultadoPrevisao.previsao(),
            resultadoPrevisao.aviso(),
            ranking,
            itensComTendencia(consolidacao, itensComTaxa),
            IndicadoresParaLLM.de(indicadores),
            ConclusaoDeterministica.de(indicadores, resultadoPrevisao.previsao())
        );
    }

    /**
     * O modelo redige, o codigo classifica. Sem isso o painel voltava a estampar desperdicio ALTO
     * sem nenhuma medicao de sobra por tras — o modelo apenas repetia o campo que ja chegava
     * classificado no payload.
     */
    private AnaliseLogisticaOutput comClassificacoesDeterministicas(
        AnaliseLogisticaOutput saida, ConclusaoDeterministica conclusao
    ) {
        if (conclusao == null) return saida;
        return new AnaliseLogisticaOutput(saida.resumoExecutivo(), conclusao.nivelAceitacao(),
            conclusao.riscoDesperdicio(), saida.evidencias(), saida.recomendacoes(),
            saida.observacaoLimitacoes());
    }

    // A fila pode avançar enquanto as consultas são feitas. A fotografia prevalece também
    // nos campos legados enviados à previsão/LLM, para não enviar duas contagens diferentes.
    private ConsolidacaoConsumoResponse alinharConsolidacaoAFotografia(
        ConsolidacaoConsumoResponse original, IndicadoresLogisticos indicadores) {
        if (indicadores == null || indicadores.execucaoPlanejamento() == null) return original;
        var execucao = indicadores.execucaoPlanejamento();
        return new ConsolidacaoConsumoResponse(original.data(), original.turno(), original.cardapioId(),
            original.cardapio(), original.itensCardapio(), original.receitasCardapio(),
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
                consolidacao.data(), consolidacao.turno(), receitas);

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
                consolidacao.turno(),
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
     * Serie de consumo dos dias anteriores no mesmo turno, para a previsao deixar de ser
     * circular: sem ela o servico devolve o consumo do proprio dia como estimativa.
     */
    private List<PrevisaoConsumoInput.DiaConsumo> consumosDosDiasAnteriores(
        ConsolidacaoConsumoResponse consolidacao
    ) {
        return auditoriaRepository.consumosPorDia(
                consolidacao.turno(),
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
