package br.com.frankcardoso.merenda.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ApplicationConfigTest {

    private static final class RelogioControlavel extends Clock {
        private final AtomicReference<Instant> agora;

        RelogioControlavel(Instant inicial) {
            this.agora = new AtomicReference<>(inicial);
        }

        void avancarMinutos(long minutos) {
            agora.updateAndGet(i -> i.plusSeconds(minutos * 60));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return zone;
                }

                @Override
                public Clock withZone(ZoneId outra) {
                    return RelogioControlavel.this.withZone(outra);
                }

                @Override
                public Instant instant() {
                    return RelogioControlavel.this.instant();
                }
            };
        }

        @Override
        public Instant instant() {
            return agora.get();
        }
    }

    @Test
    void semHorarioUsaORelogioReal() {
        var real = new RelogioControlavel(Instant.parse("2026-11-09T16:37:00Z"));

        assertThat(ApplicationConfig.relogio(real, "").instant()).isEqualTo(real.instant());
        assertThat(ApplicationConfig.relogio(real, null).instant()).isEqualTo(real.instant());
    }

    @Test
    void comHorarioComecaNaDataRealNoHorarioInformado() {
        var real = new RelogioControlavel(Instant.parse("2026-11-09T16:37:00Z"));

        assertThat(ApplicationConfig.relogio(real, "12:00").instant())
                .isEqualTo(Instant.parse("2026-11-09T15:00:00Z"));
    }

    @Test
    void usaADataDaZonaOperacional() {
        var real = new RelogioControlavel(Instant.parse("2026-11-10T01:30:00Z"));

        assertThat(ApplicationConfig.relogio(real, "07:10").instant())
                .isEqualTo(Instant.parse("2026-11-09T10:10:00Z"));
    }

    @Test
    void relogioDeDemonstracaoAvancaComORelogioReal() {
        var real = new RelogioControlavel(Instant.parse("2026-11-09T16:37:00Z"));
        var demo = ApplicationConfig.relogio(real, "12:00");

        real.avancarMinutos(3);

        assertThat(demo.instant()).isEqualTo(Instant.parse("2026-11-09T15:03:00Z"));
    }

    @Test
    void horarioInvalidoFalhaNaSubida() {
        var real = new RelogioControlavel(Instant.parse("2026-11-09T16:37:00Z"));

        assertThatThrownBy(() -> ApplicationConfig.relogio(real, "25:00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("merenda.demo.horario");
    }
}
