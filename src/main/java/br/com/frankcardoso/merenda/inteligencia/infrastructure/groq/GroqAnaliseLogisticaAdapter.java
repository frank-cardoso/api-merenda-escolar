package br.com.frankcardoso.merenda.inteligencia.infrastructure.groq;

import br.com.frankcardoso.merenda.inteligencia.infrastructure.chat.ChatClientAnaliseLogisticaAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("groqAnaliseLogisticaAdapter")
@Profile("gemini")
public class GroqAnaliseLogisticaAdapter extends ChatClientAnaliseLogisticaAdapter {

    public GroqAnaliseLogisticaAdapter(
        OpenAiChatModel groqChatModel,
        ObjectMapper objectMapper,
        @Value("${merenda.ia.fallback.modelo:openai/gpt-oss-20b}") String modelo
    ) {
        super(ChatClient.builder(groqChatModel).build(), objectMapper, "groq", modelo);
    }
}
