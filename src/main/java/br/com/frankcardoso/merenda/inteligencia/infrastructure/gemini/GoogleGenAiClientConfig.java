package br.com.frankcardoso.merenda.inteligencia.infrastructure.gemini;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Sobrescreve o Client autoconfigurado pelo Spring AI para impor um timeout HTTP real
 * (conexao/leitura) na chamada ao Gemini. Sem isso, uma chamada que trava sem erro
 * (sem 503, sem resposta nenhuma) fica presa indefinidamente no socket, e o unico limite
 * vira o timeout de aplicacao do RelatorioIAWorker, que nao cancela a chamada de fato.
 *
 * O SDK do google-genai tambem faz retry interno por conta propria (HttpRetryOptions),
 * independente do RetryTemplate do Spring AI. Sem desligar isso aqui, os dois retries
 * se empilham e o tempo total facilmente ultrapassa o timeout de aplicacao, fazendo o
 * relatorio falhar por "nao respondeu em Xs" mesmo com o timeout HTTP configurado.
 */
@Configuration
@Profile("gemini")
public class GoogleGenAiClientConfig {

    @Bean
    Client googleGenAiClient(
        @Value("${spring.ai.google.genai.api-key}") String apiKey,
        @Value("${merenda.ia.http-timeout-segundos:10}") int timeoutSegundos
    ) {
        return Client.builder()
            .apiKey(apiKey)
            .httpOptions(HttpOptions.builder()
                .timeout(timeoutSegundos * 1000)
                .retryOptions(HttpRetryOptions.builder()
                    .attempts(1)
                    .build())
                .build())
            .build();
    }
}
