package br.com.frankcardoso.merenda.relatorio.application;

import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoInput;
import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoOutput;
import br.com.frankcardoso.merenda.analytics.application.port.PrevisaoConsumoPort;
import br.com.frankcardoso.merenda.gestao.api.ConsolidacaoConsumoResponse;
import br.com.frankcardoso.merenda.gestao.application.ConsolidacaoConsumoService;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaInput;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaOutput;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaPort;
import br.com.frankcardoso.merenda.relatorio.domain.RelatorioIA;
import br.com.frankcardoso.merenda.relatorio.infrastructure.RelatorioIARepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class RelatorioIAWorker {

    private final RelatorioIARepository repository;
    private final ConsolidacaoConsumoService consolidacaoService;
    private final AnaliseLogisticaPort analisePort;
    private final PrevisaoConsumoPort previsaoPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String provedor;
    private final String modelo;
    private final Duration timeoutAnaliseIA;

    public RelatorioIAWorker(
        RelatorioIARepository repository,
        ConsolidacaoConsumoService consolidacaoService,
        AnaliseLogisticaPort analisePort,
        PrevisaoConsumoPort previsaoPort,
        ObjectMapper objectMapper,
        Clock clock,
        @Value("${merenda.ia.provedor:fake}") String provedor,
        @Value("${merenda.ia.modelo:regras-locais-v1}") String modelo,
        @Value("${merenda.ia.timeout-segundos:30}") long timeoutSegundos
    ) {
        this.repository = repository;
        this.consolidacaoService = consolidacaoService;
        this.analisePort = analisePort;
        this.previsaoPort = previsaoPort;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.provedor = provedor;
        this.modelo = modelo;
        this.timeoutAnaliseIA = Duration.ofSeconds(timeoutSegundos);
    }

    @Async("relatorioExecutor")
    public void processar(UUID relatorioId) {
        RelatorioIA relatorio = repository.findById(relatorioId)
            .orElseThrow(() -> new RelatorioNaoEncontradoException(relatorioId));

        try {
            ConsolidacaoConsumoResponse consolidacao = consolidacaoService.consolidar(
                relatorio.getDataReferencia(), relatorio.getTurno());
            AnaliseLogisticaInput input = criarInput(consolidacao, preverConsumo(consolidacao));

            relatorio.iniciar(provedor, modelo, escreverJson(input), Instant.now(clock));
            relatorio = repository.saveAndFlush(relatorio);

            var resultado = analisarComTimeout(input);
            if (resultado.saida() == null) {
                throw new IllegalStateException("O provedor de IA retornou resposta vazia");
            }

            relatorio.concluir(resultado.provedor(), resultado.modelo(), escreverJson(resultado.saida()),
                resultado.saida().resumoExecutivo(), Instant.now(clock));
            repository.save(relatorio);
        } catch (Exception exception) {
            relatorio.falhar(mensagemSegura(exception), Instant.now(clock));
            repository.save(relatorio);
        }
    }

    private AnaliseLogisticaInput criarInput(
        ConsolidacaoConsumoResponse consolidacao,
        ResultadoPrevisao resultadoPrevisao
    ) {
        return new AnaliseLogisticaInput(
            consolidacao.data(),
            consolidacao.turno(),
            consolidacao.cardapio(),
            consolidacao.quantidadePlanejada(),
            consolidacao.consumosAutorizados(),
            consolidacao.tentativasBloqueadas(),
            consolidacao.taxaConsumoPlanejado(),
            consolidacao.sobraEstimada(),
            resultadoPrevisao.previsao(),
            resultadoPrevisao.aviso()
        );
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
                consolidacao.sobraEstimada()
            ));

            return new ResultadoPrevisao(previsao, null);
        } catch (Exception exception) {
            return new ResultadoPrevisao(null, mensagemSegura(exception));
        }
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
