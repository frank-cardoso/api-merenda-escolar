package br.com.frankcardoso.merenda.inteligencia.infrastructure.chat;

import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaInput;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaOutput;
import br.com.frankcardoso.merenda.inteligencia.application.port.AnaliseLogisticaPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Logica de prompt e serializacao compartilhada entre adapters de chat (Gemini, DeepSeek, ...).
 * Cada provedor so precisa fornecer um ChatClient ja configurado.
 */
public abstract class ChatClientAnaliseLogisticaAdapter implements AnaliseLogisticaPort {

    protected static final String INSTRUCAO = """
        Voce e um analista de alimentacao escolar. Analise apenas os indicadores agregados recebidos.
        Nao invente causas, nao tome decisoes operacionais e explicite limitacoes dos dados.
        Classifique nivelAceitacao como ALTA, MEDIA ou BAIXA e riscoDesperdicio como ALTO, MEDIO ou BAIXO.
        Produza evidencias objetivas e recomendacoes prudentes. Nao solicite dados pessoais de alunos.
        """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final String provedor;
    private final String modelo;

    protected ChatClientAnaliseLogisticaAdapter(
        ChatClient chatClient, ObjectMapper objectMapper, String provedor, String modelo
    ) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.provedor = provedor;
        this.modelo = modelo;
    }

    @Override
    public String provedor() {
        return provedor;
    }

    @Override
    public String modelo() {
        return modelo;
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
