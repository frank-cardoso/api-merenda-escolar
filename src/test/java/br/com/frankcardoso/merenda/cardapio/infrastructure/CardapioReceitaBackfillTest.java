package br.com.frankcardoso.merenda.cardapio.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import br.com.frankcardoso.merenda.catalogo.domain.Receita;
import br.com.frankcardoso.merenda.catalogo.infrastructure.ReceitaRepository;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CardapioReceitaBackfillTest {

    @Autowired CardapioReceitaBackfill backfill;
    @Autowired CardapioRepository cardapios;
    @Autowired ReceitaRepository receitas;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Cardapio salvar(LocalDate data, Turno turno, String itensJson) {
        return cardapios.save(new Cardapio(UUID.randomUUID(), data, turno, "Teste", "",
            itensJson, 100, true));
    }

    @Test
    void devePreencherReceitaIdPeloNomeExato() throws Exception {
        var receita = receitas.save(new Receita(UUID.randomUUID(), "Arroz do backfill", null));
        var cardapio = salvar(LocalDate.of(2031, 3, 10), Turno.MANHA,
            "[{\"nome\":\"Arroz do backfill\",\"quantidade\":\"5 kg\"}]");

        backfill.preencher();

        var itens = objectMapper.readTree(
            cardapios.findById(cardapio.getId()).orElseThrow().getItensJson());
        assertThat(itens.get(0).path("receitaId").asText()).isEqualTo(receita.getId().toString());
        assertThat(itens.get(0).path("quantidade").asText()).isEqualTo("5 kg");
    }

    @Test
    void deveDeixarSemVinculoOItemQueNaoExisteNoCatalogo() throws Exception {
        var cardapio = salvar(LocalDate.of(2031, 3, 11), Turno.TARDE,
            "[{\"nome\":\"Prato que nao existe no catalogo\",\"quantidade\":null}]");

        backfill.preencher();

        var itens = objectMapper.readTree(
            cardapios.findById(cardapio.getId()).orElseThrow().getItensJson());
        // Sem correspondencia o backfill nao regrava, entao o campo fica ausente, nao nulo.
        assertThat(itens.get(0).hasNonNull("receitaId")).isFalse();
    }

    @Test
    void naoDeveAlterarCardapioQueJaTemVinculo() throws Exception {
        var receita = receitas.save(new Receita(UUID.randomUUID(), "Feijao do backfill", null));
        var original = "[{\"receitaId\":\"" + receita.getId()
            + "\",\"nome\":\"Feijao do backfill\",\"quantidade\":\"2 kg\"}]";
        var cardapio = salvar(LocalDate.of(2031, 3, 12), Turno.NOITE, original);

        backfill.preencher();

        assertThat(cardapios.findById(cardapio.getId()).orElseThrow().getItensJson())
            .isEqualTo(original);
    }
}
