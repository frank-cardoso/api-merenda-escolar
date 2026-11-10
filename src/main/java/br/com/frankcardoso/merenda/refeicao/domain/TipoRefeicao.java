package br.com.frankcardoso.merenda.refeicao.domain;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Refeição da grade: janela [início, fim) do relógio e turnos com direito a ela. */
@Entity
@Table(name = "tipo_refeicao")
public class TipoRefeicao {

    @Id
    private UUID id;

    @Column(nullable = false, length = 60)
    private String nome;

    @Column(nullable = false)
    private int ordem;

    @Column(nullable = false)
    private LocalTime inicio;

    @Column(nullable = false)
    private LocalTime fim;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tipo_refeicao_turno", joinColumns = @JoinColumn(name = "tipo_refeicao_id"))
    @Column(name = "turno", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Set<Turno> turnos = new HashSet<>();

    protected TipoRefeicao() {
    }

    public TipoRefeicao(UUID id, String nome, int ordem, LocalTime inicio, LocalTime fim, Set<Turno> turnos) {
        this.id = id;
        this.nome = nome;
        this.ordem = ordem;
        this.inicio = inicio;
        this.fim = fim;
        this.turnos = new HashSet<>(turnos);
    }

    public boolean contem(LocalTime horario) {
        return !horario.isBefore(inicio) && horario.isBefore(fim);
    }

    public boolean atende(Turno turno) {
        return turnos.contains(turno);
    }

    public boolean sobrepoe(TipoRefeicao outra) {
        return inicio.isBefore(outra.fim) && outra.inicio.isBefore(fim);
    }

    public void atualizar(String nome, LocalTime inicio, LocalTime fim, Set<Turno> turnos) {
        this.nome = nome;
        this.inicio = inicio;
        this.fim = fim;
        this.turnos.clear();
        this.turnos.addAll(turnos);
    }

    public void definirOrdem(int ordem) {
        this.ordem = ordem;
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public int getOrdem() { return ordem; }
    public LocalTime getInicio() { return inicio; }
    public LocalTime getFim() { return fim; }
    public Set<Turno> getTurnos() { return Set.copyOf(turnos); }
}
