package br.com.frankcardoso.merenda.inteligencia.infrastructure.gemini;

import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaInput;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaOutput;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("gemini")
public class GeminiAnaliseLogisticaAdapter implements AnaliseLogisticaPort {

    private static final String INSTRUCAO = """
        Voce e um analista de alimentacao escolar. Analise apenas os indicadores agregados recebidos.
        Nao invente causas, nao tome decisoes operacionais e explicite limitacoes dos dados.
        Classifique nivelAceitacao como ALTA, MEDIA ou BAIXA e riscoDesperdicio como ALTO, MEDIO ou BAIXO.
        Produza evidencias objetivas e recomendacoes prudentes. Nao solicite dados pessoais de alunos.
        """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public GeminiAnaliseLogisticaAdapter(ChatClient.Builder builder, ObjectMapper objectMapper) {
        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public AnaliseLogisticaOutput analisar(AnaliseLogisticaInput input) {
        String dados = serializar(input);

        return chatClient.prompt()
            .system(INSTRUCAO)
            .user(usuario -> usuario.text("""
                Analise os dados agregados abaixo e responda no formato estruturado solicitado:
                {dados}
                """).param("dados", dados))
            .call()
            .entity(AnaliseLogisticaOutput.class);
    }

    private String serializar(AnaliseLogisticaInput input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Nao foi possivel serializar os dados agregados", exception);
        }
    }
}
