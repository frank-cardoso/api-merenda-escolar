package br.com.frankcardoso.merenda.aluno.infrastructure;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlunoRepository extends JpaRepository<Aluno, UUID> {
    Optional<Aluno> findByCodigoPublicoAndAtivoTrue(String codigoPublico);
}
