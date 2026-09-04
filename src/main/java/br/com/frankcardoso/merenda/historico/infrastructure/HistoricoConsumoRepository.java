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

public interface HistoricoConsumoRepository extends JpaRepository<HistoricoConsumo, UUID> {

    /**
     * Serie diaria de planejado x servido para um turno, do mais recente para o mais antigo.
     * Agrega todas as escolas e itens do dia.
     */
    @Query("""
        SELECT h.data                        AS data,
               SUM(h.quantidadePlanejada)    AS planejado,
               COALESCE(SUM(h.quantidadeServida), 0) AS servido
        FROM HistoricoConsumo h
        WHERE h.turno = :turno
          AND h.data < :ate
        GROUP BY h.data
        ORDER BY h.data DESC
        """)
    List<DiaAgregado> resumoDiario(@Param("turno") Turno turno,
                                   @Param("ate") LocalDate ate,
                                   Pageable pageable);

    /**
     * Taxa de execucao por item: com que frequencia um item planejado teve execucao lancada.
     * E este recorte que permite correlacionar prato com aceitacao.
     */
    @Query("""
        SELECT h.item                                          AS item,
               COUNT(h)                                        AS vezesPlanejado,
               COUNT(h.quantidadeServida)                       AS vezesServido
        FROM HistoricoConsumo h
        WHERE h.turno = :turno
          AND h.data >= :desde
          AND h.data < :ate
        GROUP BY h.item
        HAVING COUNT(h) >= :minimoOcorrencias
        ORDER BY (1.0 * COUNT(h.quantidadeServida) / COUNT(h)) ASC
        """)
    List<ItemAgregado> taxaPorItem(@Param("turno") Turno turno,
                                   @Param("desde") LocalDate desde,
                                   @Param("ate") LocalDate ate,
                                   @Param("minimoOcorrencias") long minimoOcorrencias,
                                   Pageable pageable);

    /**
     * Taxa historica de execucao dos itens informados — tipicamente os do cardapio do dia.
     *
     * Existe porque o ranking global dos piores itens nao responde a pergunta util: um item de
     * execucao baixa pode nao estar entre os dez piores e ainda assim explicar o consumo de
     * hoje. Aqui a consulta e dirigida ao que foi realmente servido.
     */
    @Query("""
        SELECT h.item                              AS item,
               COUNT(h)                            AS vezesPlanejado,
               COUNT(h.quantidadeServida)          AS vezesServido
        FROM HistoricoConsumo h
        WHERE h.turno = :turno
          AND h.data < :ate
          AND h.item IN :itens
        GROUP BY h.item
        ORDER BY (1.0 * COUNT(h.quantidadeServida) / COUNT(h)) ASC
        """)
    List<ItemAgregado> taxaDosItens(@Param("turno") Turno turno,
                                    @Param("ate") LocalDate ate,
                                    @Param("itens") List<String> itens);

    interface DiaAgregado {
        LocalDate getData();
        long getPlanejado();
        long getServido();
    }

    interface ItemAgregado {
        String getItem();
        long getVezesPlanejado();
        long getVezesServido();
    }
}
