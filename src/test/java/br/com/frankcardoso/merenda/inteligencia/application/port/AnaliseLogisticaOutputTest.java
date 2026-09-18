package br.com.frankcardoso.merenda.inteligencia.application.port;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnaliseLogisticaOutputTest {
    @Test
    void rejeitaRespostaEstruturalmenteInconsistente() {
        var output = new AnaliseLogisticaOutput("", "QUALQUER", "ALTO", null, List.of(), null);
        assertThatThrownBy(output::validar).isInstanceOf(IllegalStateException.class);
    }
}
