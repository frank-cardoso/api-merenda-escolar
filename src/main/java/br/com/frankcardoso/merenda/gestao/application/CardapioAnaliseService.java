package br.com.frankcardoso.merenda.gestao.application;

import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.gestao.api.CardapioAnaliseResponse;
import br.com.frankcardoso.merenda.medicao.infrastructure.MedicaoSobraRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardapioAnaliseService {
    private static final TypeReference<List<Item>> ITENS = new TypeReference<>() {};

    private final CardapioRepository cardapios;
    private final MedicaoSobraRepository medicoes;
    private final ObjectMapper objectMapper;

    public CardapioAnaliseService(CardapioRepository cardapios, MedicaoSobraRepository medicoes,
        ObjectMapper objectMapper) {
        this.cardapios = cardapios;
        this.medicoes = medicoes;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<CardapioAnaliseResponse> listar(LocalDate inicio, LocalDate fim, Turno turno) {
        var grupos = cardapios.findAllByDataBetweenAndTurnoAndAtivoTrueOrderByDataAsc(inicio, fim, turno)
            .stream().map(this::ler).filter(item -> !item.itens().isEmpty())
            .collect(Collectors.groupingBy(this::chave, LinkedHashMap::new,
                Collectors.toList()));

        return grupos.entrySet().stream().map(entrada -> {
            var ocorrencias = entrada.getValue();
            var primeiro = ocorrencias.get(0);
            var datas = ocorrencias.stream().map(Ocorrencia::data).toList();
            var datasFechamento = primeiro.analisavel()
                ? medicoes.datasComFechamentoCompleto(datas, turno,
                    primeiro.receitas(), primeiro.receitas().size())
                : List.<LocalDate>of();
            return new CardapioAnaliseResponse(
                entrada.getKey(), primeiro.nome(), primeiro.itens(), primeiro.receitas(),
                datas, datasFechamento, ocorrencias.size(), primeiro.analisavel(), primeiro.motivo());
        }).sorted(Comparator.comparing(CardapioAnaliseResponse::nome)).toList();
    }

    private Ocorrencia ler(Cardapio cardapio) {
        try {
            var itens = objectMapper.readValue(cardapio.getItensJson(), ITENS);
            var receitas = itens.stream().map(Item::receitaId).toList();
            var analisavel = receitas.stream().allMatch(id -> id != null)
                && receitas.stream().distinct().count() == receitas.size();
            var receitasOrdenadas = itens.stream().filter(item -> item.receitaId() != null)
                .sorted(Comparator.comparing(Item::receitaId)).toList();
            return new Ocorrencia(cardapio.getData(), cardapio.getNomeRefeicao(),
                receitasOrdenadas.stream().map(Item::nome).toList(), receitasOrdenadas.stream()
                    .map(Item::receitaId).distinct().toList(), analisavel,
                analisavel ? null : "Item sem vínculo único com o catálogo de receitas.");
        } catch (JsonProcessingException exception) {
            return new Ocorrencia(cardapio.getData(), cardapio.getNomeRefeicao(), List.of(), List.of(),
                false, "Itens do cardápio estão em formato inválido.");
        }
    }

    private String chave(List<UUID> receitas) {
        return receitas.stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
    }

    private String chave(Ocorrencia ocorrencia) {
        if (ocorrencia.analisavel()) return chave(ocorrencia.receitas());
        return "invalido:" + ocorrencia.data() + ":" + String.join(",", ocorrencia.itens());
    }

    private record Item(UUID receitaId, String nome, String quantidade) {}
    private record Ocorrencia(LocalDate data, String nome, List<String> itens, List<UUID> receitas,
        boolean analisavel, String motivo) {}
}
