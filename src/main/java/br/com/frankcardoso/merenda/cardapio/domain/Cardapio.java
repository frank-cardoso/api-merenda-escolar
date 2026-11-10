package br.com.frankcardoso.merenda.cardapio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cardapio")
public class Cardapio {

    @Id
    private UUID id;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "tipo_refeicao_id", nullable = false)
    private UUID tipoRefeicaoId;

    @Column(name = "nome_refeicao", nullable = false, length = 150)
    private String nomeRefeicao;

    @Column(length = 500)
    private String descricao;

    @Column(name = "itens_json", nullable = false, columnDefinition = "CHARACTER LARGE OBJECT")
    private String itensJson;

    @Column(name = "quantidade_planejada", nullable = false)
    private int quantidadePlanejada;

    @Column(nullable = false)
    private boolean ativo;

    protected Cardapio() {
    }

    public Cardapio(UUID id, LocalDate data, UUID tipoRefeicaoId, String nomeRefeicao, String descricao,
                    String itensJson, int quantidadePlanejada, boolean ativo) {
        this.id = id;
        this.data = data;
        this.tipoRefeicaoId = tipoRefeicaoId;
        this.nomeRefeicao = nomeRefeicao;
        this.descricao = descricao;
        this.itensJson = itensJson;
        this.quantidadePlanejada = quantidadePlanejada;
        this.ativo = ativo;
    }

    public void atualizar(LocalDate data, UUID tipoRefeicaoId, String nomeRefeicao, String descricao,
                          String itensJson, int quantidadePlanejada) {
        exigirAtivo();
        this.data = data;
        this.tipoRefeicaoId = tipoRefeicaoId;
        this.nomeRefeicao = nomeRefeicao;
        this.descricao = descricao;
        this.itensJson = itensJson;
        this.quantidadePlanejada = quantidadePlanejada;
    }

    /**
     * Marca o cardapio como inativo em vez de remove-lo. A linha continua existindo porque
     * auditoria_consumo referencia o cardapio servido e esse historico nao pode ser perdido.
     */
    /**
     * Preenche o vinculo com o catalogo de receitas sem mexer no resto do cardapio.
     *
     * Existe so para o backfill unico da V7: cardapios gravados antes dela tem item sem
     * receitaId. Nao passa por exigirAtivo() de proposito — cardapio desativado guarda historico
     * e tambem precisa do vinculo se um dia for reativado.
     */
    public void vincularReceitas(String itensJson) {
        this.itensJson = itensJson;
    }

    public void desativar() {
        exigirAtivo();
        this.ativo = false;
    }

    private void exigirAtivo() {
        if (!ativo) {
            throw new IllegalStateException("Cardapio inativo nao pode ser alterado");
        }
    }

    public UUID getId() { return id; }
    public LocalDate getData() { return data; }
    public UUID getTipoRefeicaoId() { return tipoRefeicaoId; }
    public String getNomeRefeicao() { return nomeRefeicao; }
    public String getDescricao() { return descricao; }
    public String getItensJson() { return itensJson; }
    public int getQuantidadePlanejada() { return quantidadePlanejada; }
    public boolean isAtivo() { return ativo; }
}
