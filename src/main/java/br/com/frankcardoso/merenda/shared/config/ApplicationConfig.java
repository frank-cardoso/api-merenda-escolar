package br.com.frankcardoso.merenda.shared.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    /** Fuso em que a escola opera: define o "hoje" da fila e das telas. */
    public static final ZoneId ZONA_OPERACIONAL = ZoneId.of("America/Sao_Paulo");

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
