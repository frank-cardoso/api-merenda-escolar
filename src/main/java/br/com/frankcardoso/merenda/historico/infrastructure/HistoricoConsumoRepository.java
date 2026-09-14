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
     * Taxa historica de execucao de todos os itens ja vistos no turno, sem filtro de nome.
     *
     * O casamento com os itens do cardapio do dia e feito em Java (HistoricoConsumoService),
     * porque o nome cadastrado no cardapio nem sempre bate com o nome do legado ("arroz" vs
     * "Arroz branco") — comparacao exata em JPQL (h.item IN :itens) deixava esses casos de fora.
     */
    @Query("""
        SELECT h.item                              AS item,
               COUNT(h)                            AS vezesPlanejado,
               COUNT(h.quantidadeServida)          AS vezesServido
        FROM HistoricoConsumo h
        WHERE h.turno = :turno
          AND h.data < :ate
        GROUP BY h.item
        ORDER BY (1.0 * COUNT(h.quantidadeServida) / COUNT(h)) ASC
        """)
    List<ItemAgregado> taxaPorTodosOsItens(@Param("turno") Turno turno,
                                           @Param("ate") LocalDate ate);

    /**
     * Serie diaria de planejado x servido de itens especificos, do mais antigo para o mais
     * recente. Alimenta o calculo de tendencia (Python): olha se a taxa de execucao de um item
     * esta subindo ou caindo ao longo do tempo, o que a taxa unica agregada de taxaPorTodosOsItens
     * nao mostra.
     *
     * Recebe os nomes ja resolvidos pelo casamento feito em HistoricoConsumoService — aqui a
     * comparacao pode ser exata porque os nomes vem direto do historico, nao do cardapio.
     */
    @Query("""
        SELECT h.item                              AS item,
               h.data                              AS data,
               SUM(h.quantidadePlanejada)          AS planejado,
               COALESCE(SUM(h.quantidadeServida), 0) AS servido
        FROM HistoricoConsumo h
        WHERE h.turno = :turno
          AND h.data < :ate
          AND h.item IN :itens
        GROUP BY h.item, h.data
        ORDER BY h.item ASC, h.data ASC
        """)
    List<ItemDiaAgregado> serieDiariaPorItem(@Param("turno") Turno turno,
                                             @Param("ate") LocalDate ate,
                                             @Param("itens") List<String> itens);

    interface ItemAgregado {
        String getItem();
        long getVezesPlanejado();
        long getVezesServido();
    }

    interface ItemDiaAgregado {
        String getItem();
        LocalDate getData();
        long getPlanejado();
        long getServido();
    }
}
