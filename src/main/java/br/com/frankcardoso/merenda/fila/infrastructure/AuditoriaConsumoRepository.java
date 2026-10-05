package br.com.frankcardoso.merenda.fila.infrastructure;

import br.com.frankcardoso.merenda.fila.domain.AuditoriaConsumo;
import br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditoriaConsumoRepository extends JpaRepository<AuditoriaConsumo, UUID> {
    @Query("""
        SELECT a.aluno.turma AS turma, COUNT(a) AS consumos, COUNT(DISTINCT a.aluno.id) AS alunos
        FROM AuditoriaConsumo a
        WHERE a.dataOperacional = :data AND a.turno = :turno
          AND a.resultado = br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo.AUTORIZADO
        GROUP BY a.aluno.turma ORDER BY a.aluno.turma
        """)
    List<AtendimentoTurma> atendimentosPorTurma(@Param("data") LocalDate data, @Param("turno") Turno turno);

    interface AtendimentoTurma {
        String getTurma();
        long getConsumos();
        long getAlunos();
    }

    boolean existsByAlunoIdAndDataOperacionalAndTurnoAndResultadoAndInstanteGreaterThanEqual(
        UUID alunoId,
        LocalDate dataOperacional,
        Turno turno,
        ResultadoConsumo resultado,
        Instant instante
    );

    long countByDataOperacionalAndTurnoAndResultado(
        LocalDate dataOperacional,
        Turno turno,
        ResultadoConsumo resultado
    );

    /**
     * Consumos autorizados por dia, do mais recente para o mais antigo.
     *
     * Alimenta a previsao do servico Python, que espera a mesma grandeza do consumo do dia
     * (contagem de bipagens autorizadas). Nao serve o historico de itens: aquele agrega varias
     * escolas e itens, em escala diferente, e distorceria a media.
     */
    @Query("""
        SELECT a.dataOperacional AS data, COUNT(a) AS autorizados
        FROM AuditoriaConsumo a
        WHERE a.turno = :turno
          AND a.resultado = br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo.AUTORIZADO
          AND a.dataOperacional < :ate
        GROUP BY a.dataOperacional
        ORDER BY a.dataOperacional DESC
        """)
    List<ConsumoDiario> consumosPorDia(@Param("turno") Turno turno,
                                       @Param("ate") LocalDate ate,
                                       Pageable pageable);

    interface ConsumoDiario {
        LocalDate getData();
        long getAutorizados();
    }
}
