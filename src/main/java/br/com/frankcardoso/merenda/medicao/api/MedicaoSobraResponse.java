package br.com.frankcardoso.merenda.medicao.api;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.medicao.domain.MedicaoSobra;
import java.time.LocalDate;
import java.util.UUID;

public record MedicaoSobraResponse(
    UUID id, LocalDate data, Turno turno, UUID receitaId, int porcoesPreparadas,
    int porcoesServidas, int sobraNaoDistribuida, int restoNoPrato, String origem
) {
    public static MedicaoSobraResponse de(MedicaoSobra medicao) {
        return new MedicaoSobraResponse(medicao.getId(), medicao.getData(), medicao.getTurno(),
            medicao.getReceitaId(), medicao.getPorcoesPreparadas(), medicao.getPorcoesServidas(),
            medicao.getSobraNaoDistribuida(), medicao.getRestoNoPrato(), medicao.getOrigem());
    }
}
