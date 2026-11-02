package br.com.frankcardoso.merenda.turma.infrastructure;

import br.com.frankcardoso.merenda.turma.domain.Enturmacao;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnturmacaoRepository extends JpaRepository<Enturmacao, UUID> {

    @Query("""
        SELECT e FROM Enturmacao e
        WHERE e.aluno.id = :alunoId AND e.dataInicio <= :data
          AND (e.dataFim IS NULL OR e.dataFim >= :data)
        ORDER BY e.dataInicio DESC
        """)
    List<Enturmacao> vigentesEm(@Param("alunoId") UUID alunoId, @Param("data") LocalDate data);

    /**
     * Turma vigente para a fila. Tolerante de proposito: se dado inconsistente entrar por fora do
     * servico e houver vigencias sobrepostas, vale a de inicio mais recente em vez de derrubar a bipagem.
     */
    default Optional<Enturmacao> vigenteEm(UUID alunoId, LocalDate data) {
        return vigentesEm(alunoId, data).stream().findFirst();
    }

    @EntityGraph(attributePaths = "turma")
    List<Enturmacao> findByAlunoIdOrderByDataInicioDesc(UUID alunoId);

    boolean existsByTurmaId(UUID turmaId);
}
