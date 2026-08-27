package br.com.frankcardoso.merenda.fila.infrastructure;

import br.com.frankcardoso.merenda.fila.domain.AuditoriaConsumo;
import br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaConsumoRepository extends JpaRepository<AuditoriaConsumo, UUID> {
    boolean existsByChaveConsumoAutorizado(String chaveConsumoAutorizado);

    long countByDataOperacionalAndTurnoAndResultado(
        LocalDate dataOperacional,
        Turno turno,
        ResultadoConsumo resultado
    );
}
