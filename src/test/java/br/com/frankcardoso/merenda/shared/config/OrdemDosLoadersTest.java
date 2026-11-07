package br.com.frankcardoso.merenda.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioReceitaBackfill;
import br.com.frankcardoso.merenda.catalogo.infrastructure.CatalogoLoader;
import br.com.frankcardoso.merenda.historico.infrastructure.HistoricoFakeLoader;
import br.com.frankcardoso.merenda.medicao.infrastructure.MedicaoSobraFakeLoader;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

/** O Spring só lê @Order do método ouvinte; no tipo ele é ignorado. */
class OrdemDosLoadersTest {

    @Test
    void ouvintesDeApplicationReadyTemOrdemSequencialNoMetodo() throws Exception {
        List<Class<?>> loaders = List.of(CatalogoLoader.class, HistoricoFakeLoader.class,
            MedicaoSobraFakeLoader.class, CardapioReceitaBackfill.class,
            RostosDemonstracaoLoader.class, DadosDemonstracaoLoader.class);

        for (int i = 0; i < loaders.size(); i++) {
            var metodo = java.util.Arrays.stream(loaders.get(i).getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(EventListener.class)).findFirst().orElseThrow();
            var ouvinte = metodo.getAnnotation(EventListener.class);
            assertThat(ouvinte).as(loaders.get(i).getSimpleName()).isNotNull();
            assertThat(ouvinte.value()).containsExactly(ApplicationReadyEvent.class);
            var ordem = metodo.getAnnotation(Order.class);
            assertThat(ordem).as("@Order em " + loaders.get(i).getSimpleName()).isNotNull();
            assertThat(ordem.value()).as(loaders.get(i).getSimpleName()).isEqualTo(i + 1);
        }
    }
}
