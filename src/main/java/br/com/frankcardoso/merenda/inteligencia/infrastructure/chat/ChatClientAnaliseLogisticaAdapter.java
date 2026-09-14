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

        Para relacionar cardapio e consumo, use itensDoCardapioComTendencia: ele traz, por item
        servido hoje, a taxa historica de execucao e a tendencia (QUEDA, ESTAVEL ou ALTA)
        calculada sobre a serie recente dessa taxa. Avalie o risco logistico focando na tendencia
        de cada item, nao so na taxa isolada de hoje — um item em QUEDA e candidato a explicar o
        consumo do dia mesmo com taxa historica media. tendencia pode vir ausente (dado
        indisponivel); nesse caso baseie-se so na taxa.

        Quando itensDoCardapioComTendencia vier preenchido, uma das evidencias deve nomear cada
        item com sua taxa e, quando disponivel, sua tendencia (exemplo: "Salada de alface 5,3%
        em queda, Banana 42,4% estavel"). Nao substitua isso por uma mencao generica a "itens de
        baixa aceitacao": sem o nome e o numero, quem le o relatorio nao sabe qual item revisar.

        Nao conclua nada a partir de itensComMenorExecucao: aquele e o ranking dos piores itens
        da base em geral, e um item pode ter execucao baixa sem aparecer nele.

        Sobre o historico, respeite dois limites:
        - Taxa de execucao baixa pode indicar baixa aceitacao OU apenas ausencia de registro
          pela escola. Os dados nao distinguem os dois casos, entao nao afirme que houve rejeicao
          de alimento sem ressalvar essa ambiguidade.
        - Uma taxa media proxima de 30% e o comportamento normal desta base, nao um problema em si.
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
