package br.com.frankcardoso.merenda.catalogo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Item que pode ir ao cardapio, com identidade propria.
 *
 * O nome e rotulo de exibicao: pode ser corrigido sem quebrar historico, medicao nem composicao,
 * porque todas apontam para o id. {@code codigoExterno} e o lugar do RECEITAS.ID do legado quando
 * houver integracao.
 */
@Entity
@Table(name = "receita")
public class Receita {

    @Id
    private UUID id;

    @Column(name = "codigo_externo", length = 40)
    private String codigoExterno;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false)
    private boolean ativo = true;

    protected Receita() {
    }

    public Receita(UUID id, String nome, String codigoExterno) {
        this.id = id;
        this.nome = nome;
        this.codigoExterno = codigoExterno;
        this.ativo = true;
    }

    public UUID getId() { return id; }
    public String getCodigoExterno() { return codigoExterno; }
    public String getNome() { return nome; }
    public boolean isAtivo() { return ativo; }
}
