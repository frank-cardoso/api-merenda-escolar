package br.com.frankcardoso.merenda.fechamento.api;

import br.com.frankcardoso.merenda.fechamento.domain.SituacaoFechamento;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record RefeicaoDoDiaResponse(
    UUID tipoRefeicaoId,
    String refeicao,
    int ordem,
    @JsonFormat(pattern = "HH:mm") LocalTime inicio,
    @JsonFormat(pattern = "HH:mm") LocalTime fim,
    SituacaoFechamento situacao,
    UUID cardapioId,
    int itensCardapio,
    int itensMedidos,
    long consumosAutorizados,
    Instant fechadoEm
) {
}
