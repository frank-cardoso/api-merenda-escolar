package br.com.frankcardoso.merenda.turma.domain;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "enturmacao")
public class Enturmacao {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turma_id", nullable = false)
    private Turma turma;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Column(name = "codigo_origem", length = 40)
    private String codigoOrigem;

    protected Enturmacao() {
    }

    public Enturmacao(UUID id, Aluno aluno, Turma turma, LocalDate dataInicio, LocalDate dataFim,
                      String codigoOrigem) {
        if (dataFim != null && dataFim.isBefore(dataInicio)) {
            throw new IllegalArgumentException("dataFim nao pode ser anterior a dataInicio");
        }
        this.id = id;
        this.aluno = aluno;
        this.turma = turma;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.codigoOrigem = codigoOrigem;
    }

    /** Fecha o vinculo aberto; o fim nunca pode ficar antes do inicio. */
    public void encerrar(LocalDate dataFim) {
        if (dataFim.isBefore(dataInicio)) {
            throw new IllegalArgumentException("dataFim nao pode ser anterior a dataInicio");
        }
        this.dataFim = dataFim;
    }

    /** Dois vinculos se sobrepoem quando compartilham ao menos um dia; fim nulo e aberto. */
    public boolean sobrepoe(LocalDate inicio, LocalDate fim) {
        return (fim == null || !dataInicio.isAfter(fim)) && (dataFim == null || !dataFim.isBefore(inicio));
    }

    public boolean vigenteEm(LocalDate data) {
        return !dataInicio.isAfter(data) && (dataFim == null || !dataFim.isBefore(data));
    }

    public UUID getId() { return id; }
    public Aluno getAluno() { return aluno; }
    public Turma getTurma() { return turma; }
    public LocalDate getDataInicio() { return dataInicio; }
    public LocalDate getDataFim() { return dataFim; }
    public String getCodigoOrigem() { return codigoOrigem; }
}
