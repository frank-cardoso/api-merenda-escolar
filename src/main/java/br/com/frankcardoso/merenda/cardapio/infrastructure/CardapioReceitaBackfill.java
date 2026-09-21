package br.com.frankcardoso.merenda.cardapio.infrastructure;

import br.com.frankcardoso.merenda.cardapio.api.ItemCardapioDto;
import br.com.frankcardoso.merenda.catalogo.infrastructure.ReceitaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Preenche {@code receitaId} nos cardapios gravados antes do catalogo existir.
 *
 * A V7 trocou identidade de item por id e deliberadamente nao fez backfill, para nao repetir o
 * casamento por nome que ela existe para eliminar. Isso estava rigido demais: o objetivo era tirar
 * o casamento por nome do fluxo continuo, nao proibir uma passada unica de migracao. Sem ela, todo
 * cardapio anterior fica invisivel para medicao e analise ate ser regravado a mao — quatro num
 * prototipo, milhares numa base real.
 *
 * O casamento e por nome exato, nunca aproximado. Item que nao existir no catalogo fica com
 * receitaId nulo e e registrado no log, em vez de ser adivinhado.
 *
 * Idempotente: item que ja tem id e ignorado, entao reiniciar nao refaz nada.
 */
@Component
@Order(4)
public class CardapioReceitaBackfill {

    private static final Logger LOG = LoggerFactory.getLogger(CardapioReceitaBackfill.class);
    private static final TypeReference<List<ItemCardapioDto>> LISTA = new TypeReference<>() {
    };

    private final CardapioRepository cardapios;
    private final ReceitaRepository receitas;
    private final ObjectMapper objectMapper;

    public CardapioReceitaBackfill(CardapioRepository cardapios, ReceitaRepository receitas,
                                   ObjectMapper objectMapper) {
        this.cardapios = cardapios;
        this.receitas = receitas;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void preencher() {
        if (receitas.count() == 0) return;

        int vinculados = 0;
        var semCorrespondencia = new ArrayList<String>();

        for (var cardapio : cardapios.findAll()) {
            var itens = ler(cardapio.getItensJson());
            if (itens.isEmpty() || itens.stream().allMatch(item -> item.receitaId() != null)) continue;

            var resolvidos = itens.stream().map(item -> {
                if (item.receitaId() != null) return item;
                var receita = receitas.findByNome(item.nome()).orElse(null);
                if (receita == null) {
                    semCorrespondencia.add(item.nome());
                    return item;
                }
                return new ItemCardapioDto(receita.getId(), receita.getNome(), item.quantidade());
            }).toList();

            if (resolvidos.equals(itens)) continue;
            cardapio.vincularReceitas(escrever(resolvidos));
            cardapios.save(cardapio);
            vinculados++;
        }

        if (vinculados > 0) {
            LOG.info("Backfill de receitas: {} cardápio(s) vinculados ao catálogo", vinculados);
        }
        if (!semCorrespondencia.isEmpty()) {
            LOG.warn("Backfill de receitas: {} item(ns) sem correspondência exata no catálogo, "
                + "seguem sem vínculo: {}", semCorrespondencia.size(),
                semCorrespondencia.stream().distinct().toList());
        }
    }

    private List<ItemCardapioDto> ler(String itensJson) {
        if (itensJson == null || itensJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(itensJson, LISTA);
        } catch (Exception excecao) {
            LOG.warn("Cardápio com itens em formato inválido, ignorado no backfill: {}",
                excecao.getMessage());
            return List.of();
        }
    }

    private String escrever(List<ItemCardapioDto> itens) {
        try {
            return objectMapper.writeValueAsString(itens);
        } catch (Exception excecao) {
            throw new IllegalStateException("Não foi possível serializar os itens do cardápio", excecao);
        }
    }
}
