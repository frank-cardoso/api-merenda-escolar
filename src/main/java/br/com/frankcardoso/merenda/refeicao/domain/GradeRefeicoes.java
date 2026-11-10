package br.com.frankcardoso.merenda.refeicao.domain;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class GradeRefeicoes {

    static final String FORA_DO_HORARIO = "FORA_DO_HORARIO_DE_REFEICAO";
    static final String NAO_PREVISTA_PARA_TURMA = "REFEICAO_NAO_PREVISTA_PARA_TURMA";

    private final List<TipoRefeicao> refeicoes;

    private GradeRefeicoes(List<TipoRefeicao> refeicoes) {
        this.refeicoes = refeicoes;
    }

    public static GradeRefeicoes de(List<TipoRefeicao> refeicoes) {
        if (refeicoes.isEmpty()) {
            throw new GradeRefeicoesInvalidaException("A grade de refeições está vazia.");
        }
        for (int i = 0; i < refeicoes.size(); i++) {
            for (int j = i + 1; j < refeicoes.size(); j++) {
                var a = refeicoes.get(i);
                var b = refeicoes.get(j);
                if (a.sobrepoe(b) && a.getTurnos().stream().anyMatch(b::atende)) {
                    throw new GradeRefeicoesInvalidaException(
                        "As refeições %s e %s se sobrepõem para o mesmo turno.".formatted(a.getNome(), b.getNome()));
                }
            }
        }
        return new GradeRefeicoes(refeicoes.stream().sorted(Comparator.comparingInt(TipoRefeicao::getOrdem)).toList());
    }

    public Optional<String> bloqueioDoHorario(LocalTime horario) {
        return refeicoes.stream().anyMatch(r -> emUso(r, horario))
            ? Optional.empty()
            : Optional.of(FORA_DO_HORARIO);
    }

    /** {@code turnoDaTurma} nulo é o aluno sem turma: vale a refeição de menor ordem na janela. */
    public ResolucaoRefeicao resolver(LocalTime horario, Turno turnoDaTurma) {
        var naJanela = refeicoes.stream().filter(r -> emUso(r, horario)).toList();
        if (naJanela.isEmpty()) {
            return ResolucaoRefeicao.bloqueada(FORA_DO_HORARIO);
        }
        if (turnoDaTurma == null) {
            return ResolucaoRefeicao.autorizada(naJanela.get(0));
        }
        return naJanela.stream()
            .filter(r -> r.atende(turnoDaTurma))
            .findFirst()
            .map(ResolucaoRefeicao::autorizada)
            .orElseGet(() -> ResolucaoRefeicao.naoPrevista(naJanela.get(0)));
    }

    /** Refeição sem nenhum turno está aposentada: não abre janela nem disputa horário. */
    private static boolean emUso(TipoRefeicao refeicao, LocalTime horario) {
        return refeicao.contem(horario) && !refeicao.getTurnos().isEmpty();
    }
}
