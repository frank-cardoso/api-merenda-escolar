package br.com.frankcardoso.merenda.shared.config;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    private static final Logger log = LoggerFactory.getLogger(ApplicationConfig.class);

    /** Fuso em que a escola opera: define o "hoje" da fila e das telas. */
    public static final ZoneId ZONA_OPERACIONAL = ZoneId.of("America/Sao_Paulo");

    @Bean
    Clock clock(@Value("${merenda.demo.horario:}") String horario) {
        return relogio(Clock.systemUTC(), horario);
    }

    /**
     * Com {@code HH:mm}, devolve um relógio que começa hoje nesse horário (na zona operacional)
     * e segue avançando junto com o relógio real.
     */
    static Clock relogio(Clock real, String horarioDemonstracao) {
        if (horarioDemonstracao == null || horarioDemonstracao.isBlank()) {
            return real;
        }
        LocalTime horario;
        try {
            horario = LocalTime.parse(horarioDemonstracao.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "merenda.demo.horario inválido: '" + horarioDemonstracao + "' (esperado HH:mm)", e);
        }
        var alvo = LocalDate.now(real.withZone(ZONA_OPERACIONAL))
                .atTime(horario)
                .atZone(ZONA_OPERACIONAL)
                .toInstant();
        log.info("Relógio de demonstração: hoje às {}", horario);
        return Clock.offset(real, Duration.between(real.instant(), alvo));
    }
}
