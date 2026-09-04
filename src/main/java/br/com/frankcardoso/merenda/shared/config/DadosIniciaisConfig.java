package br.com.frankcardoso.merenda.shared.config;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class DadosIniciaisConfig {

    private static final ZoneId ZONA_OPERACIONAL = ZoneId.of("America/Sao_Paulo");

    @Bean
    CommandLineRunner carregarDadosIniciais(AlunoRepository alunos, CardapioRepository cardapios, Clock clock) {
        return args -> {
            if (alunos.count() == 0) {
                Instant agora = Instant.now(clock);
                alunos.saveAll(List.of(
                    new Aluno(UUID.randomUUID(), "ALU-001", "2026001", "Ana Souza", "6A", true, agora),
                    new Aluno(UUID.randomUUID(), "ALU-002", "2026002", "Bruno Lima", "6A", true, agora),
                    new Aluno(UUID.randomUUID(), "ALU-003", "2026003", "Carla Santos", "7B", true, agora)
                ));
            }

            // Os cardapios ficam a cargo do DadosDemonstracaoLoader, que usa o mesmo
            // vocabulario de itens do historico sintetico. Criar aqui tambem faria o cardapio
            // de hoje nascer com nomes que o historico nao conhece (este runner roda antes),
            // impedindo a analise de relacionar prato com execucao.
        };
    }
}
