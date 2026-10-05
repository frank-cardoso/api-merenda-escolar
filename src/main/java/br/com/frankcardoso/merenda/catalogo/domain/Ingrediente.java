package br.com.frankcardoso.merenda.catalogo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Ingrediente com identidade propria. No legado corresponde a INGREDIENTES.ID. */
@Entity
@Table(name = "ingrediente")
public class Ingrediente {

    @Id
    private UUID id;

    @Column(name = "codigo_externo", length = 40)
    private String codigoExterno;

    @Column(nullable = false, length = 80)
    private String nome;

    protected Ingrediente() {
    }

    public Ingrediente(UUID id, String nome, String codigoExterno) {
        this.id = id;
        this.nome = nome;
        this.codigoExterno = codigoExterno;
    }

    public UUID getId() { return id; }
    public String getCodigoExterno() { return codigoExterno; }
    public String getNome() { return nome; }
}
