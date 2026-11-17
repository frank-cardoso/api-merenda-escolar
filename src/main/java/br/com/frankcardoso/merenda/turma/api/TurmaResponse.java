package br.com.frankcardoso.merenda.turma.api;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.turma.domain.Turma;
import java.util.UUID;

public record TurmaResponse(UUID id, String codigo, String descricao, Turno turno, Integer quantidadeMaximaAlunos) {

    static TurmaResponse de(Turma turma) {
        return new TurmaResponse(turma.getId(), turma.getCodigo(), turma.getDescricao(), turma.getTurno(),
            turma.getQuantidadeMaximaAlunos());
    }
}
