package br.com.frankcardoso.merenda.fila.domain;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "auditoria_consumo")
public class AuditoriaConsumo {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aluno_id")
    private Aluno aluno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cardapio_id")
    private Cardapio cardapio;

    @Column(name = "data_operacional", nullable = false)
    private LocalDate dataOperacional;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Turno turno;

    @Column(nullable = false)
    private Instant instante;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_identificacao", nullable = false, length = 20)
    private MetodoIdentificacao metodoIdentificacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultadoConsumo resultado;

    @Column(length = 60)
    private String motivo;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "chave_consumo_autorizado", unique = true, length = 150)
    private String chaveConsumoAutorizado;

    protected AuditoriaConsumo() {
    }

    public AuditoriaConsumo(UUID id, Aluno aluno, Cardapio cardapio, LocalDate dataOperacional,
                            Turno turno, Instant instante, MetodoIdentificacao metodoIdentificacao,
                            ResultadoConsumo resultado, String motivo, UUID correlationId,
                            String chaveConsumoAutorizado) {
        this.id = id;
        this.aluno = aluno;
        this.cardapio = cardapio;
        this.dataOperacional = dataOperacional;
        this.turno = turno;
        this.instante = instante;
        this.metodoIdentificacao = metodoIdentificacao;
        this.resultado = resultado;
        this.motivo = motivo;
        this.correlationId = correlationId;
        this.chaveConsumoAutorizado = chaveConsumoAutorizado;
    }
}
