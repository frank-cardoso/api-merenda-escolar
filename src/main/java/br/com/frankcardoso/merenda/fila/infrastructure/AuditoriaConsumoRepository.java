package br.com.frankcardoso.merenda.fila.infrastructure;

import br.com.frankcardoso.merenda.fila.domain.AuditoriaConsumo;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaConsumoRepository extends JpaRepository<AuditoriaConsumo, UUID> {
    boolean existsByChaveConsumoAutorizado(String chaveConsumoAutorizado);
}
