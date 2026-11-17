package br.com.frankcardoso.merenda.medicao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Sobra medida de um item em um dia e refeicao, em porcoes.
 *
 * As duas perdas ficam separadas porque respondem a perguntas diferentes:
 * {@code sobraNaoDistribuida} e comida que nunca chegou ao prato (erro de producao) e
 * {@code restoNoPrato} e comida servida e devolvida — a unica das duas que mede rejeicao.
 */
@Entity
@Table(name = "medicao_sobra")
public class MedicaoSobra {

    @Id
    private UUID id;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "tipo_refeicao_id", nullable = false)
    private UUID tipoRefeicaoId;

    @Column(name = "escola_id", nullable = false)
    private int escolaId;

    @Column(name = "receita_id", nullable = false)
    private UUID receitaId;

    @Column(name = "porcoes_preparadas", nullable = false)
    private int porcoesPreparadas;

    @Column(name = "porcoes_servidas", nullable = false)
    private int porcoesServidas;

    @Column(name = "sobra_nao_distribuida", nullable = false)
    private int sobraNaoDistribuida;

    @Column(name = "resto_no_prato", nullable = false)
    private int restoNoPrato;

    @Column(nullable = false, length = 20)
    private String origem;

    @Column(name = "registrado_em", nullable = false)
    private Instant registradoEm;

    protected MedicaoSobra() {
    }

    public MedicaoSobra(UUID id, LocalDate data, UUID tipoRefeicaoId, int escolaId, UUID receitaId,
                        int porcoesPreparadas, int porcoesServidas, int sobraNaoDistribuida,
                        int restoNoPrato, String origem, Instant registradoEm) {
        if (porcoesPreparadas < 0 || porcoesServidas < 0 || sobraNaoDistribuida < 0 || restoNoPrato < 0) {
            throw new MedicaoSobraInconsistenteException("Porções não podem ser negativas");
        }
        if (porcoesServidas + sobraNaoDistribuida != porcoesPreparadas) {
            throw new MedicaoSobraInconsistenteException(
                "Porções preparadas (%d) devem ser a soma de servidas (%d) e sobra não distribuída (%d)"
                    .formatted(porcoesPreparadas, porcoesServidas, sobraNaoDistribuida));
        }
        if (restoNoPrato > porcoesServidas) {
            throw new MedicaoSobraInconsistenteException(
                "Resto no prato (%d) não pode superar o que foi servido (%d)"
                    .formatted(restoNoPrato, porcoesServidas));
        }
        this.id = id;
        this.data = data;
        this.tipoRefeicaoId = tipoRefeicaoId;
        this.escolaId = escolaId;
        this.receitaId = receitaId;
        this.porcoesPreparadas = porcoesPreparadas;
        this.porcoesServidas = porcoesServidas;
        this.sobraNaoDistribuida = sobraNaoDistribuida;
        this.restoNoPrato = restoNoPrato;
        this.origem = origem;
        this.registradoEm = registradoEm;
    }

    public UUID getId() { return id; }
    public LocalDate getData() { return data; }
    public UUID getTipoRefeicaoId() { return tipoRefeicaoId; }
    public int getEscolaId() { return escolaId; }
    public UUID getReceitaId() { return receitaId; }
    public int getPorcoesPreparadas() { return porcoesPreparadas; }
    public int getPorcoesServidas() { return porcoesServidas; }
    public int getSobraNaoDistribuida() { return sobraNaoDistribuida; }
    public int getRestoNoPrato() { return restoNoPrato; }
    public String getOrigem() { return origem; }
    public Instant getRegistradoEm() { return registradoEm; }
}
