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
               sum(m.porcoesServidas) as servidas,
               sum(m.restoNoPrato) as resto,
               count(m) as amostra
        from MedicaoSobra m join Receita r on r.id = m.receitaId
        where m.data between :inicio and :fim and m.turno = :turno
        group by m.receitaId, r.nome
        order by r.nome
        """)
    List<RestoReceita> restoPorReceita(@Param("inicio") LocalDate inicio,
        @Param("fim") LocalDate fim, @Param("turno") Turno turno);

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
        long getServidas();
        long getResto();
        long getAmostra();
    }

}
