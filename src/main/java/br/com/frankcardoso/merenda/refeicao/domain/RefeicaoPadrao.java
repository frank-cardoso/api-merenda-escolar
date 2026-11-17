package br.com.frankcardoso.merenda.refeicao.domain;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** As cinco refeições semeadas pela migration, com os ids fixos dela. */
public enum RefeicaoPadrao {
    CAFE_DA_MANHA("5e1a0000-0000-4000-8000-000000000001", "Café da manhã", Turno.INTEGRAL),
    LANCHE_DA_MANHA("5e1a0000-0000-4000-8000-000000000002", "Lanche da manhã", Turno.MANHA, Turno.INTEGRAL),
    ALMOCO("5e1a0000-0000-4000-8000-000000000003", "Almoço", Turno.INTEGRAL),
    LANCHE_DA_TARDE("5e1a0000-0000-4000-8000-000000000004", "Lanche da tarde", Turno.TARDE, Turno.INTEGRAL),
    LANCHE_DA_NOITE("5e1a0000-0000-4000-8000-000000000005", "Lanche da noite", Turno.NOITE);

    private final UUID id;
    private final String nome;
    private final Set<Turno> turnos;

    RefeicaoPadrao(String id, String nome, Turno... turnos) {
        this.id = UUID.fromString(id);
        this.nome = nome;
        this.turnos = EnumSet.copyOf(List.of(turnos));
    }

    public UUID id() { return id; }
    public String nome() { return nome; }
    /** Turnos que a migration semeou; a grade editada na tela pode divergir deles. */
    public Set<Turno> turnos() { return Set.copyOf(turnos); }

    public static Optional<RefeicaoPadrao> doId(UUID id) {
        return Arrays.stream(values()).filter(r -> r.id.equals(id)).findFirst();
    }

    /** Refeição que o turno único do legado representava; a mesma regra da migration. */
    public static RefeicaoPadrao doTurnoLegado(Turno turno) {
        return switch (turno) {
            case MANHA -> LANCHE_DA_MANHA;
            case TARDE -> LANCHE_DA_TARDE;
            case NOITE -> LANCHE_DA_NOITE;
            case INTEGRAL -> ALMOCO;
        };
    }

    /** Refeição pelo nome gravado no histórico do legado, por prefixo; vazio quando nenhum casa. */
    public static Optional<RefeicaoPadrao> doNomeLegado(String nome) {
        if (nome.startsWith("Cafe d")) return Optional.of(CAFE_DA_MANHA);
        if (nome.startsWith("Lanche da manha")) return Optional.of(LANCHE_DA_MANHA);
        if (nome.startsWith("Almoco")) return Optional.of(ALMOCO);
        if (nome.startsWith("Lanche da tarde")) return Optional.of(LANCHE_DA_TARDE);
        return Optional.empty();
    }
}
