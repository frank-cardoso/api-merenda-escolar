package br.com.frankcardoso.merenda.aluno.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "aluno")
public class Aluno {

    @Id
    private UUID id;

    @Column(name = "codigo_publico", nullable = false, unique = true, length = 40)
    private String codigoPublico;

    @Column(nullable = false, unique = true, length = 40)
    private String matricula;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, length = 80)
    private String turma;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Aluno() {
    }

    public Aluno(UUID id, String codigoPublico, String matricula, String nome, String turma, boolean ativo, Instant criadoEm) {
        this.id = id;
        this.codigoPublico = codigoPublico;
        this.matricula = matricula;
        this.nome = nome;
        this.turma = turma;
        this.ativo = ativo;
        this.criadoEm = criadoEm;
    }

    public UUID getId() { return id; }
    public String getCodigoPublico() { return codigoPublico; }
    public String getMatricula() { return matricula; }
    public String getNome() { return nome; }
    public String getTurma() { return turma; }
    public boolean isAtivo() { return ativo; }
    public Instant getCriadoEm() { return criadoEm; }
}
