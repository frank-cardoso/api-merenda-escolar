package br.com.frankcardoso.merenda.turma.domain;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "turma")
public class Turma {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Turno turno;

    @Column(name = "quantidade_maxima_alunos")
    private Integer quantidadeMaximaAlunos;

    @Column(name = "codigo_origem", length = 40)
    private String codigoOrigem;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Turma() {
    }

    public Turma(UUID id, String codigo, String descricao, Turno turno, Integer quantidadeMaximaAlunos,
                 String codigoOrigem, Instant criadoEm) {
        this.id = id;
        this.codigo = codigo;
        this.descricao = descricao;
        this.turno = turno;
        this.quantidadeMaximaAlunos = quantidadeMaximaAlunos;
        this.codigoOrigem = codigoOrigem;
        this.criadoEm = criadoEm;
    }

    public void atualizar(String codigo, String descricao, Turno turno, Integer quantidadeMaximaAlunos) {
        this.codigo = codigo;
        this.descricao = descricao;
        this.turno = turno;
        this.quantidadeMaximaAlunos = quantidadeMaximaAlunos;
    }

    public UUID getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getDescricao() { return descricao; }
    public Turno getTurno() { return turno; }
    public Integer getQuantidadeMaximaAlunos() { return quantidadeMaximaAlunos; }
    public String getCodigoOrigem() { return codigoOrigem; }
    public Instant getCriadoEm() { return criadoEm; }
}
