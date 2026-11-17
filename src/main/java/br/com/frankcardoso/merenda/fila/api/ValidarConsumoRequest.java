package br.com.frankcardoso.merenda.fila.api;

import br.com.frankcardoso.merenda.fila.domain.MetodoIdentificacao;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * Em QR_CODE o aluno vem de {@code alunoCodigo}. Em FACIAL vale so a foto: o aluno e
 * identificado entre todos os cadastrados (1:N) e {@code alunoCodigo} e ignorado.
 */
public record ValidarConsumoRequest(
    String alunoCodigo,
    @NotNull MetodoIdentificacao metodoIdentificacao,
    String fotoBase64
) {

    @AssertTrue(message = "alunoCodigo: obrigatorio para QR_CODE")
    @JsonIgnore
    public boolean isAlunoCodigoInformadoParaQrCode() {
        return metodoIdentificacao != MetodoIdentificacao.QR_CODE
            || (alunoCodigo != null && !alunoCodigo.isBlank());
    }
}
