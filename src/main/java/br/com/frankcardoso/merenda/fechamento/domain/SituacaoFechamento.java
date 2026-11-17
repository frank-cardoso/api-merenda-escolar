package br.com.frankcardoso.merenda.fechamento.domain;

import java.time.LocalDate;
import java.time.LocalTime;

public enum SituacaoFechamento {
    SEM_CARDAPIO, FECHADA, AGUARDANDO, EM_ANDAMENTO, PENDENTE;

    /** Regra de precedência na ordem do enum; a janela da refeição é [inicio, fim). */
    public static SituacaoFechamento de(boolean temCardapio, boolean completa, LocalDate data,
                                        LocalTime inicio, LocalTime fim, LocalDate hoje, LocalTime agora) {
        if (!temCardapio) return SEM_CARDAPIO;
        if (completa) return FECHADA;
        if (data.isBefore(hoje)) return PENDENTE;
        if (data.isAfter(hoje) || agora.isBefore(inicio)) return AGUARDANDO;
        return agora.isBefore(fim) ? EM_ANDAMENTO : PENDENTE;
    }
}
