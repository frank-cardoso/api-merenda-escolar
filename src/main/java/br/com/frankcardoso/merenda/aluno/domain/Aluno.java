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

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "codigo_origem", length = 40)
    private String codigoOrigem;

    @Column(name = "rosto_embedding_json", columnDefinition = "CHARACTER LARGE OBJECT")
    private String rostoEmbeddingJson;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected Aluno() {
    }

    public Aluno(UUID id, String codigoPublico, String matricula, String nome, boolean ativo,
                 String codigoOrigem, Instant criadoEm) {
        this.id = id;
        this.codigoPublico = codigoPublico;
        this.matricula = matricula;
        this.nome = nome;
        this.ativo = ativo;
        this.codigoOrigem = codigoOrigem;
        this.criadoEm = criadoEm;
    }

    public void atualizar(String matricula, String nome, boolean ativo) {
        this.matricula = matricula;
        this.nome = nome;
        this.ativo = ativo;
    }

    /** O embedding chega pronto (gerado pelo servico de reconhecimento); o aluno so guarda. */
    public void cadastrarRosto(String rostoEmbeddingJson) {
        this.rostoEmbeddingJson = rostoEmbeddingJson;
    }

    public boolean temRostoCadastrado() { return rostoEmbeddingJson != null; }

    public UUID getId() { return id; }
    public String getCodigoPublico() { return codigoPublico; }
    public String getMatricula() { return matricula; }
    public String getNome() { return nome; }
    public boolean isAtivo() { return ativo; }
    public String getCodigoOrigem() { return codigoOrigem; }
    public String getRostoEmbeddingJson() { return rostoEmbeddingJson; }
    public Instant getCriadoEm() { return criadoEm; }
}
