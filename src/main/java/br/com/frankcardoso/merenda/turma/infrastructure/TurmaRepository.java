package br.com.frankcardoso.merenda.turma.infrastructure;

import br.com.frankcardoso.merenda.turma.domain.Turma;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TurmaRepository extends JpaRepository<Turma, UUID> {
    Optional<Turma> findByCodigo(String codigo);
}
