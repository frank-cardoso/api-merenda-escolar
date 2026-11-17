package br.com.frankcardoso.merenda.shared.config;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class DadosIniciaisConfig {

    @Bean
    CommandLineRunner carregarDadosIniciais(AlunoRepository alunos, CardapioRepository cardapios, Clock clock) {
        return args -> {
            if (alunos.count() == 0) {
                Instant agora = Instant.now(clock);
                alunos.saveAll(List.of(
                    new Aluno(UUID.randomUUID(), "ALU-001", "2026001", "Ana Souza", true, null, agora),
                    new Aluno(UUID.randomUUID(), "ALU-002", "2026002", "Bruno Lima", true, null, agora),
                    new Aluno(UUID.randomUUID(), "ALU-003", "2026003", "Carla Santos", true, null, agora)
                ));
            }

            // Os cardapios ficam a cargo do DadosDemonstracaoLoader, que usa o mesmo
            // vocabulario de itens do historico sintetico. Criar aqui tambem faria o cardapio
            // de hoje nascer com nomes que o historico nao conhece (este runner roda antes),
            // impedindo a analise de relacionar prato com execucao.
        };
    }
}
