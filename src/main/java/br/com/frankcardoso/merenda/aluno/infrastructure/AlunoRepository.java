package br.com.frankcardoso.merenda.aluno.infrastructure;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AlunoRepository extends JpaRepository<Aluno, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Aluno> findByCodigoPublicoAndAtivoTrue(String codigoPublico);
}
