package br.com.frankcardoso.merenda.refeicao.api;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.refeicao.domain.TipoRefeicao;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record TipoRefeicaoResponse(UUID id, String nome, int ordem, LocalTime inicio, LocalTime fim,
                                   List<Turno> turnos) {
    public static TipoRefeicaoResponse de(TipoRefeicao refeicao) {
        return new TipoRefeicaoResponse(refeicao.getId(), refeicao.getNome(), refeicao.getOrdem(),
            refeicao.getInicio(), refeicao.getFim(), refeicao.getTurnos().stream().sorted().toList());
    }
}
