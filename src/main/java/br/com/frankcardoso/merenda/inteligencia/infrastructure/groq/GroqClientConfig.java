package br.com.frankcardoso.merenda.inteligencia.infrastructure.groq;

import java.time.Duration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

/**
 * Configura o Groq como provedor secundario (fallback do Gemini), via API compativel
 * com OpenAI. Nao usa a autoconfiguracao padrao do starter openai porque
 * "spring.ai.model.chat=google-genai" desliga essa autoconfiguracao inteira; o client aqui
 * e montado manualmente para os dois provedores coexistirem ao mesmo tempo.
 */
@Configuration
@Profile("gemini")
public class GroqClientConfig {

    @Bean
    OpenAiApi groqApi(
        @Value("${merenda.ia.fallback.base-url:https://api.groq.com/openai/v1}") String baseUrl,
        @Value("${GROQ_API_KEY:}") String apiKey,
        @Value("${merenda.ia.fallback.http-timeout-segundos:10}") int timeoutSegundos
    ) {
        var timeout = Duration.ofSeconds(timeoutSegundos);
        var requestFactory = ClientHttpRequestFactoryBuilder.detect()
            .build(ClientHttpRequestFactorySettings.defaults().withTimeouts(timeout, timeout));

        return OpenAiApi.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
            .build();
    }

    @Bean
    OpenAiChatModel groqChatModel(
        OpenAiApi groqApi,
        RetryTemplate retryTemplate,
        @Value("${merenda.ia.fallback.modelo:openai/gpt-oss-20b}") String modelo
    ) {
        var options = OpenAiChatOptions.builder()
            .model(modelo)
            .temperature(0.2)
            .build();

        return OpenAiChatModel.builder()
            .openAiApi(groqApi)
            .defaultOptions(options)
            .retryTemplate(retryTemplate)
            .build();
    }
}
