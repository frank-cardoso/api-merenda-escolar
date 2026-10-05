package br.com.frankcardoso.merenda.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class CatalogoItensDemonstracaoTest {

    @Test
    void cardapioDemonstracaoNasceComIdentidadeDaReceita() throws Exception {
        var catalogo = new CatalogoItensDemonstracao(new DefaultResourceLoader());
        var cardapio = catalogo.sortearCardapio(new Random(2026));
        JsonNode itens = new ObjectMapper().readTree(cardapio.comoJson());

        assertThat(itens.isArray()).isTrue();
        assertThat(itens).isNotEmpty();
        for (JsonNode item : itens) {
            assertThat(item.path("receitaId").isTextual()).isTrue();
            assertThat(item.path("receitaId").asText()).isNotBlank();
        }
    }

    @Test
    void cardapioPrincipalMantemOsCincoItensDaAnalise() {
        var catalogo = new CatalogoItensDemonstracao(new DefaultResourceLoader());

        assertThat(catalogo.cardapioPrincipal(new Random(2026)).itens())
            .extracting(CatalogoItensDemonstracao.Item::nome)
            .containsExactlyInAnyOrder("Arroz branco", "Macarrao ao sugo",
                "Leite com achocolatado", "Cuscuz", "Maca");
    }
}
