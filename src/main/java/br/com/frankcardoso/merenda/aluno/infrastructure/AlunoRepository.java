package br.com.frankcardoso.merenda.aluno.infrastructure;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlunoRepository extends JpaRepository<Aluno, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Aluno> findByCodigoPublicoAndAtivoTrue(String codigoPublico);

    /** Serializa alteracoes de vinculo do mesmo aluno: a checagem de sobreposicao so vale sob este lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Aluno a WHERE a.id = :id")
    Optional<Aluno> buscarParaAtualizar(@Param("id") UUID id);

    boolean existsByMatricula(String matricula);

    boolean existsByCodigoPublico(String codigoPublico);
}
