package br.com.frankcardoso.merenda.historico.infrastructure;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.historico.domain.HistoricoConsumo;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Todo agrupamento e por {@code receitaId}; {@code Receita.nome} entra por join, so como rotulo.
 *
 * A versao anterior agrupava pelo nome e o cardapio era casado em Java por substring. Isso deixava
 * duas regras de identidade no mesmo sistema e ja tinha produzido correlacao sobre nada (item 3 da
 * RETROSPECTIVA).
 */
public interface HistoricoConsumoRepository extends JpaRepository<HistoricoConsumo, UUID> {

    @Query("""
        SELECT h.receitaId AS receitaId, r.nome AS item, h.origem AS origem, h.escolaId AS escola,
               COUNT(h) AS planejamentos, COUNT(h.quantidadeServida) AS execucoes
        FROM HistoricoConsumo h JOIN Receita r ON r.id = h.receitaId
        WHERE h.turno = :turno AND h.data >= :inicio AND h.data <= :fim
        GROUP BY h.receitaId, r.nome, h.origem, h.escolaId
        ORDER BY r.nome, h.origem, h.escolaId
        """)
    List<ExecucaoItem> execucoesNaJanela(@Param("inicio") LocalDate inicio,
        @Param("fim") LocalDate fim, @Param("turno") Turno turno);

    interface ExecucaoItem {
        UUID getReceitaId();
        String getItem();
        String getOrigem();
        int getEscola();
        long getPlanejamentos();
        long getExecucoes();
    }

    /**
     * Taxa de execucao por receita: com que frequencia uma receita planejada teve execucao lancada.
     */
    @Query("""
        SELECT h.receitaId                   AS receitaId,
               r.nome                        AS item,
               COUNT(h)                      AS vezesPlanejado,
               COUNT(h.quantidadeServida)    AS vezesServido
        FROM HistoricoConsumo h JOIN Receita r ON r.id = h.receitaId
        WHERE h.turno = :turno
          AND h.data >= :desde
          AND h.data < :ate
        GROUP BY h.receitaId, r.nome
        HAVING COUNT(h) >= :minimoOcorrencias
        ORDER BY (1.0 * COUNT(h.quantidadeServida) / COUNT(h)) ASC
        """)
    List<ItemAgregado> taxaPorItem(@Param("turno") Turno turno,
                                   @Param("desde") LocalDate desde,
                                   @Param("ate") LocalDate ate,
                                   @Param("minimoOcorrencias") long minimoOcorrencias,
                                   Pageable pageable);

    /** Taxa historica das receitas informadas. Filtro por id, sem casamento aproximado. */
    @Query("""
        SELECT h.receitaId                   AS receitaId,
               r.nome                        AS item,
               COUNT(h)                      AS vezesPlanejado,
               COUNT(h.quantidadeServida)    AS vezesServido
        FROM HistoricoConsumo h JOIN Receita r ON r.id = h.receitaId
        WHERE h.turno = :turno
          AND h.data < :ate
          AND h.receitaId IN :receitas
        GROUP BY h.receitaId, r.nome
        ORDER BY (1.0 * COUNT(h.quantidadeServida) / COUNT(h)) ASC
        """)
    List<ItemAgregado> taxaPorReceitas(@Param("turno") Turno turno,
                                       @Param("ate") LocalDate ate,
                                       @Param("receitas") List<UUID> receitas);

    /**
     * Serie diaria de planejado x servido das receitas informadas, do mais antigo para o mais
     * recente. Alimenta o calculo de tendencia no servico Python, que a taxa agregada nao mostra.
     */
    @Query("""
        SELECT h.receitaId                        AS receitaId,
               r.nome                             AS item,
               h.data                             AS data,
               SUM(h.quantidadePlanejada)         AS planejado,
               COALESCE(SUM(h.quantidadeServida), 0) AS servido
        FROM HistoricoConsumo h JOIN Receita r ON r.id = h.receitaId
        WHERE h.turno = :turno
          AND h.data < :ate
          AND h.receitaId IN :receitas
        GROUP BY h.receitaId, r.nome, h.data
        ORDER BY h.receitaId ASC, h.data ASC
        """)
    List<ItemDiaAgregado> serieDiariaPorReceita(@Param("turno") Turno turno,
                                                @Param("ate") LocalDate ate,
                                                @Param("receitas") List<UUID> receitas);

    interface ItemAgregado {
        UUID getReceitaId();
        String getItem();
        long getVezesPlanejado();
        long getVezesServido();
    }

    interface ItemDiaAgregado {
        UUID getReceitaId();
        String getItem();
        LocalDate getData();
        long getPlanejado();
        long getServido();
    }
}
