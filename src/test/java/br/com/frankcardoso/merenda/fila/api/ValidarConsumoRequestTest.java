package br.com.frankcardoso.merenda.fila.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.fila.domain.MetodoIdentificacao;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class ValidarConsumoRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void qrSemCodigoEhInvalido() {
        var violacoes = validator.validate(new ValidarConsumoRequest(" ", MetodoIdentificacao.QR_CODE, null));

        assertThat(violacoes).extracting(v -> v.getMessage())
            .containsExactly("alunoCodigo: obrigatorio para QR_CODE");
    }

    @Test
    void qrComCodigoEhValido() {
        assertThat(validator.validate(new ValidarConsumoRequest("ALU-9001", MetodoIdentificacao.QR_CODE, null)))
            .isEmpty();
    }

    @Test
    void facialSemCodigoEhValido() {
        assertThat(validator.validate(new ValidarConsumoRequest(null, MetodoIdentificacao.FACIAL, "foto"))).isEmpty();
    }
}
