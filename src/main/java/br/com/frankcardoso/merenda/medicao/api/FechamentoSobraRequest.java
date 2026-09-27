package br.com.frankcardoso.merenda.medicao.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record FechamentoSobraRequest(@NotEmpty List<@Valid MedicaoSobraRequest> medicoes) {
}
