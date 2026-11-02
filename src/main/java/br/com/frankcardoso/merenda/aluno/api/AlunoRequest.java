package br.com.frankcardoso.merenda.aluno.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * POST usa todos os campos; turmaId e dataInicio (opcionais, juntos) criam o primeiro vinculo.
 * PUT usa matricula, nome e ativo (obrigatorio); codigoPublico, turmaId e dataInicio sao ignorados.
 */
public record AlunoRequest(
    @Size(max = 40) String codigoPublico,
    @NotBlank @Size(max = 40) String matricula,
    @NotBlank @Size(max = 150) String nome,
    Boolean ativo,
    UUID turmaId,
    LocalDate dataInicio
) {
}
