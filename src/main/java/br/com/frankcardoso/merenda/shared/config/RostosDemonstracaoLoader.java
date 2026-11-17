package br.com.frankcardoso.merenda.shared.config;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrega os alunos de demonstração com o embedding facial já calculado, para o primeiro
 * reconhecimento ao vivo não depender de cadastro prévio. Roda antes do
 * DadosDemonstracaoLoader, que deixa esses alunos fora do consumo sintético.
 */
@Component
@Profile("!test")
public class RostosDemonstracaoLoader {

    private static final Logger LOG = LoggerFactory.getLogger(RostosDemonstracaoLoader.class);

    private final AlunoRepository alunos;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Resource semente;

    public RostosDemonstracaoLoader(
        AlunoRepository alunos,
        ObjectMapper objectMapper,
        Clock clock,
        @Value("${merenda.demo.rostos:classpath:demo/rostos-demo.json}") Resource semente
    ) {
        this.alunos = alunos;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.semente = semente;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(5)
    @Transactional
    public void carregar() {
        if (!semente.exists()) {
            LOG.info("Semente de rostos de demonstração ausente ({}); nada a carregar", semente);
            return;
        }

        JsonNode raiz;
        try (InputStream entrada = semente.getInputStream()) {
            raiz = objectMapper.readTree(entrada);
        } catch (Exception exception) {
            LOG.warn("Semente de rostos de demonstração ilegível: {}", exception.getMessage());
            return;
        }

        Instant agora = Instant.now(clock);
        for (JsonNode item : raiz.path("alunos")) {
            String codigo = item.path("codigoPublico").asText();
            String matricula = item.path("matricula").asText();
            String embedding = item.path("embedding").toString();

            var existente = alunos.findByCodigoPublico(codigo);
            if (existente.isPresent()) {
                Aluno aluno = existente.get();
                if (!aluno.temRostoCadastrado()) {
                    aluno.cadastrarRosto(embedding);
                    alunos.save(aluno);
                }
                continue;
            }
            if (alunos.existsByMatricula(matricula)) {
                LOG.warn("Matrícula {} da semente já pertence a outro aluno; {} não foi criado", matricula, codigo);
                continue;
            }
            Aluno novo = new Aluno(UUID.randomUUID(), codigo, matricula,
                item.path("nome").asText(), true, null, agora);
            novo.cadastrarRosto(embedding);
            alunos.save(novo);
        }
    }
}
