package br.com.frankcardoso.merenda.cardapio.infrastructure;

import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardapioRepository extends JpaRepository<Cardapio, UUID> {
    Optional<Cardapio> findByDataAndTurnoAndAtivoTrue(LocalDate data, Turno turno);
    boolean existsByDataAndTurno(LocalDate data, Turno turno);
}
