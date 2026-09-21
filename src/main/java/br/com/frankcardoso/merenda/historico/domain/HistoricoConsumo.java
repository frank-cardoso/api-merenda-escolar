package br.com.frankcardoso.merenda.historico.domain;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Registro historico de um item planejado em um dia/turno, com a quantidade servida quando a
 * execucao foi lancada.
 *
 * {@code quantidadeServida} nula significa "planejado sem execucao registrada" — no legado esse
 * e o caso mais comum (cerca de 69% das linhas) e nao representa dado faltante. No legado essa
 * distincao virou a coluna REFEICOES_SERVIDAS_QTDS.PLANEJADA.
 *
 * O item e referenciado por {@code receitaId}, nunca por nome: o nome vive em Receita e serve de
 * rotulo.
 */
@Entity
@Table(name = "historico_consumo")
public class HistoricoConsumo {

    @Id
    private UUID id;

    @Column(nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Turno turno;

    @Column(name = "escola_id", nullable = false)
    private int escolaId;

    @Column(nullable = false, length = 150)
    private String refeicao;

    @Column(name = "receita_id", nullable = false)
    private UUID receitaId;

    @Column(name = "quantidade_planejada", nullable = false)
    private int quantidadePlanejada;

    @Column(name = "quantidade_servida")
    private Integer quantidadeServida;

    @Column(nullable = false, length = 20)
    private String origem;

    protected HistoricoConsumo() {
    }

    public HistoricoConsumo(UUID id, LocalDate data, Turno turno, int escolaId, String refeicao,
                            UUID receitaId, int quantidadePlanejada, Integer quantidadeServida,
                            String origem) {
        this.id = id;
        this.data = data;
        this.turno = turno;
        this.escolaId = escolaId;
        this.refeicao = refeicao;
        this.receitaId = receitaId;
        this.quantidadePlanejada = quantidadePlanejada;
        this.quantidadeServida = quantidadeServida;
        this.origem = origem;
    }

    public UUID getId() { return id; }
    public LocalDate getData() { return data; }
    public Turno getTurno() { return turno; }
    public int getEscolaId() { return escolaId; }
    public String getRefeicao() { return refeicao; }
    public UUID getReceitaId() { return receitaId; }
    public int getQuantidadePlanejada() { return quantidadePlanejada; }
    public Integer getQuantidadeServida() { return quantidadeServida; }
    public String getOrigem() { return origem; }
}
