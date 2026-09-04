package br.com.frankcardoso.merenda.inteligencia.infrastructure.gemini;

import br.com.frankcardoso.merenda.inteligencia.infrastructure.chat.ChatClientAnaliseLogisticaAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("gemini")
public class GeminiAnaliseLogisticaAdapter extends ChatClientAnaliseLogisticaAdapter {

    public GeminiAnaliseLogisticaAdapter(
        GoogleGenAiChatModel chatModel,
        ObjectMapper objectMapper,
        @Value("${merenda.ia.modelo:gemini-3.6-flash}") String modelo
    ) {
        super(ChatClient.builder(chatModel).build(), objectMapper, "gemini", modelo);
    }
}
