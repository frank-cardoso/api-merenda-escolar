package br.com.frankcardoso.merenda.catalogo.infrastructure;

import br.com.frankcardoso.merenda.catalogo.domain.ReceitaIngrediente;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReceitaIngredienteRepository extends JpaRepository<ReceitaIngrediente, UUID> {

    /**
     * Grafo receita -> ingrediente da base inteira, para o Java montar o payload.
     *
     * O servico Python nao e dono dessa tabela: ele recebe o grafo no pedido e fica sem estado de
     * dominio, o que e o que permite plugar em outra base sem mudar nada la.
     */
    @Query("""
        select ri.receitaId as receitaId, i.id as ingredienteId, i.nome as ingredienteNome
        from ReceitaIngrediente ri join Ingrediente i on i.id = ri.ingredienteId
        where ri.receitaId in :receitas
        order by ri.receitaId, i.nome
        """)
    List<VinculoIngrediente> grafoDasReceitas(@Param("receitas") List<UUID> receitas);

    interface VinculoIngrediente {
        UUID getReceitaId();
        UUID getIngredienteId();
        String getIngredienteNome();
    }
}
