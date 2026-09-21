package br.com.frankcardoso.merenda.catalogo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Composicao de uma receita, por id nos dois lados.
 *
 * No legado o equivalente e RECEITAS_INGREDIENTES, que guarda tambem quantidade, unidade de medida
 * e medida caseira. Aqui so o vinculo, porque a analise de aceitacao por ingrediente nao usa
 * quantidade — usa presenca.
 */
@Entity
@Table(name = "receita_ingrediente")
public class ReceitaIngrediente {

    @Id
    private UUID id;

    @Column(name = "receita_id", nullable = false)
    private UUID receitaId;

    @Column(name = "ingrediente_id", nullable = false)
    private UUID ingredienteId;

    protected ReceitaIngrediente() {
    }

    public ReceitaIngrediente(UUID id, UUID receitaId, UUID ingredienteId) {
        this.id = id;
        this.receitaId = receitaId;
        this.ingredienteId = ingredienteId;
    }

    public UUID getId() { return id; }
    public UUID getReceitaId() { return receitaId; }
    public UUID getIngredienteId() { return ingredienteId; }
}
