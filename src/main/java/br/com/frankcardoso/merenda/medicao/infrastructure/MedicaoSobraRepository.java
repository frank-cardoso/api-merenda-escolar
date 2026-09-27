package br.com.frankcardoso.merenda.medicao.infrastructure;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.medicao.domain.MedicaoSobra;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicaoSobraRepository extends JpaRepository<MedicaoSobra, UUID> {

    Optional<MedicaoSobra> findByDataAndTurnoAndEscolaIdAndReceitaId(
        LocalDate data, Turno turno, int escolaId, UUID receitaId);

    List<MedicaoSobra> findAllByDataAndTurnoAndEscolaIdOrderByReceitaId(
        LocalDate data, Turno turno, int escolaId);

    /**
     * Totais do dia restritos aos itens do cardapio.
     *
     * O filtro por receita importa para a cobertura: {@code itens} precisa ser "quantas receitas do
     * cardapio foram medidas", nao "quantas medicoes existem no dia". Sem ele, uma medicao de uma
     * receita fora do cardapio inflaria a contagem e a cobertura fecharia sem ter fechado.
     */
    @Query("""
        select coalesce(sum(m.porcoesPreparadas), 0) as preparadas,
               coalesce(sum(m.porcoesServidas), 0) as servidas,
               coalesce(sum(m.sobraNaoDistribuida), 0) as sobra,
               coalesce(sum(m.restoNoPrato), 0) as resto,
               count(m) as itens
        from MedicaoSobra m
        where m.data = :data and m.turno = :turno and m.escolaId = :escolaId
          and m.receitaId in :receitasDoCardapio
        """)
    ResumoDia resumoDoDia(@Param("data") LocalDate data, @Param("turno") Turno turno,
        @Param("escolaId") int escolaId, @Param("receitasDoCardapio") List<UUID> receitasDoCardapio);

    /**
     * Resto medido por receita na janela. A atribuicao a ingrediente acontece no servico Python,
     * que recebe o grafo receita -> ingrediente no mesmo pedido.
     *
     * Agrupar por ingrediente aqui, em SQL, escondia a coocorrencia: batata herdava a rejeicao do
     * chuchu por dividirem a sopa de legumes, e o resultado ja chegava contaminado sem deixar
     * rastro. Com o dado por receita, o Python consegue comparar presenca contra ausencia.
     */
    @Query("""
        select m.receitaId as receitaId,
               r.nome as nome,
               sum(m.porcoesPreparadas) as preparadas,
               sum(m.porcoesServidas) as servidas,
               sum(m.sobraNaoDistribuida) as sobra,
               sum(m.restoNoPrato) as resto,
               count(m) as amostra
        from MedicaoSobra m join Receita r on r.id = m.receitaId
        where m.data between :inicio and :fim and m.turno = :turno
        group by m.receitaId, r.nome
        order by r.nome
        """)
    List<RestoReceita> restoPorReceita(@Param("inicio") LocalDate inicio,
        @Param("fim") LocalDate fim, @Param("turno") Turno turno);

    @Query("""
        select m.receitaId as receitaId,
               r.nome as nome,
               sum(m.porcoesPreparadas) as preparadas,
               sum(m.porcoesServidas) as servidas,
               sum(m.sobraNaoDistribuida) as sobra,
               sum(m.restoNoPrato) as resto,
               count(m) as amostra
        from MedicaoSobra m join Receita r on r.id = m.receitaId
        where m.data in :datas and m.turno = :turno
        group by m.receitaId, r.nome
        order by r.nome
        """)
    List<RestoReceita> restoPorReceitaNasDatas(@Param("datas") List<LocalDate> datas,
        @Param("turno") Turno turno);

    @Query("""
        select m.data from MedicaoSobra m
        where m.data in :datas and m.turno = :turno and m.receitaId in :receitas
        group by m.data
        having count(distinct m.receitaId) = :quantidadeReceitas
        order by m.data
        """)
    List<LocalDate> datasComFechamentoCompleto(@Param("datas") List<LocalDate> datas,
        @Param("turno") Turno turno, @Param("receitas") List<UUID> receitas,
        @Param("quantidadeReceitas") long quantidadeReceitas);

    @Query("""
        select count(distinct m.data)
        from MedicaoSobra m
        where m.data between :inicio and :fim and m.turno = :turno
        """)
    long countFechamentos(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim,
        @Param("turno") Turno turno);

    @Query("""
        select count(distinct m.data) from MedicaoSobra m
        where m.data between :inicio and :fim and m.turno = :turno
          and m.receitaId in :receitas
        """)
    long countFechamentosPorReceitas(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim,
        @Param("turno") Turno turno, @Param("receitas") List<UUID> receitas);

    @Query("""
        select count(distinct m.data)
        from MedicaoSobra m
        where m.data between :inicio and :fim and m.turno = :turno and m.receitaId = :receita
        """)
    long countDiasPorReceita(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim,
        @Param("turno") Turno turno, @Param("receita") UUID receita);

    @Query("""
        select count(distinct m.data) from MedicaoSobra m
        where m.data in :datas and m.turno = :turno and m.receitaId in :receitas
        """)
    long countFechamentosNasDatas(@Param("datas") List<LocalDate> datas,
        @Param("turno") Turno turno, @Param("receitas") List<UUID> receitas);

    @Query("""
        select count(distinct m.data) from MedicaoSobra m
        where m.data in :datas and m.turno = :turno
        """)
    long countFechamentosNasDatas(@Param("datas") List<LocalDate> datas,
        @Param("turno") Turno turno);

    @Query("""
        select count(distinct m.data) from MedicaoSobra m
        where m.data in :datas and m.turno = :turno and m.receitaId = :receita
        """)
    long countDiasPorReceitaNasDatas(@Param("datas") List<LocalDate> datas,
        @Param("turno") Turno turno, @Param("receita") UUID receita);

    @Query("""
        select distinct m.data
        from MedicaoSobra m
        where m.data between :inicio and :fim and m.turno = :turno
        order by m.data
        """)
    List<LocalDate> datasComFechamento(@Param("inicio") LocalDate inicio,
        @Param("fim") LocalDate fim, @Param("turno") Turno turno);

    @Query("""
        select distinct m.data from MedicaoSobra m
        where m.data between :inicio and :fim and m.turno = :turno
          and m.receitaId in :receitas order by m.data
        """)
    List<LocalDate> datasComFechamentoPorReceitas(@Param("inicio") LocalDate inicio,
        @Param("fim") LocalDate fim, @Param("turno") Turno turno, @Param("receitas") List<UUID> receitas);

    interface ResumoDia {
        long getPreparadas();
        long getServidas();
        long getSobra();
        long getResto();
        long getItens();
    }

    interface RestoReceita {
        UUID getReceitaId();
        String getNome();
        long getPreparadas();
        long getServidas();
        long getSobra();
        long getResto();
        long getAmostra();
    }

}
