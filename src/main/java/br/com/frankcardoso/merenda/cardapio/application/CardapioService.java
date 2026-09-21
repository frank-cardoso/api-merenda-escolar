package br.com.frankcardoso.merenda.cardapio.application;

import br.com.frankcardoso.merenda.cardapio.api.CardapioRequest;
import br.com.frankcardoso.merenda.cardapio.api.CardapioResponse;
import br.com.frankcardoso.merenda.cardapio.api.ItemCardapioDto;
import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.catalogo.infrastructure.ReceitaRepository;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardapioService {

    private static final TypeReference<List<ItemCardapioDto>> LISTA_DE_ITENS = new TypeReference<>() {
    };

    private final CardapioRepository repository;
    private final ReceitaRepository receitas;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CardapioService(CardapioRepository repository, ReceitaRepository receitas,
                           ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.receitas = receitas;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CardapioResponse> listar() {
        return repository.findAllByAtivoTrueOrderByDataDescTurnoAsc().stream()
            .map(this::paraResposta)
            .toList();
    }

    @Transactional
    public CardapioResponse criar(CardapioRequest request) {
        if (request.data().isBefore(LocalDate.now(clock))) {
            throw new CardapioDataPassadaException(request.data());
        }
        exigirCardapioAtivoInexistente(request.data(), request.turno());

        var cardapio = new Cardapio(
            UUID.randomUUID(),
            request.data(),
            request.turno(),
            request.nomeRefeicao(),
            request.descricao(),
            escreverItens(resolverReceitas(request.itens())),
            request.quantidadePlanejada(),
            true
        );

        return paraResposta(repository.save(cardapio));
    }

    @Transactional
    public CardapioResponse atualizar(UUID id, CardapioRequest request) {
        var cardapio = buscarAtivo(id);
        boolean mudouDataOuTurno = !cardapio.getData().equals(request.data())
            || cardapio.getTurno() != request.turno();
        if (mudouDataOuTurno) {
            exigirCardapioAtivoInexistente(request.data(), request.turno());
        }

        cardapio.atualizar(
            request.data(),
            request.turno(),
            request.nomeRefeicao(),
            request.descricao(),
            escreverItens(resolverReceitas(request.itens())),
            request.quantidadePlanejada()
        );

        return paraResposta(repository.save(cardapio));
    }

    @Transactional
    public void desativar(UUID id) {
        var cardapio = buscarAtivo(id);
        cardapio.desativar();
        repository.save(cardapio);
    }

    private Cardapio buscarAtivo(UUID id) {
        return repository.findByIdAndAtivoTrue(id)
            .orElseThrow(() -> new CardapioNaoEncontradoPorIdException(id));
    }

    private void exigirCardapioAtivoInexistente(LocalDate data, Turno turno) {
        if (repository.existsByDataAndTurnoAndAtivoTrue(data, turno)) {
            throw new CardapioDuplicadoException(data, turno);
        }
    }

    private CardapioResponse paraResposta(Cardapio cardapio) {
        return new CardapioResponse(
            cardapio.getId(),
            cardapio.getData(),
            cardapio.getTurno(),
            cardapio.getNomeRefeicao(),
            cardapio.getDescricao(),
            lerItens(cardapio.getItensJson()),
            cardapio.getQuantidadePlanejada(),
            cardapio.isAtivo()
        );
    }

    /**
     * Garante id em todo item gravado.
     *
     * O formulario atual manda so o nome. Em vez de exigir que o front mude de uma vez, o id e
     * resolvido aqui por igualdade exata no catalogo — e nome fora do catalogo e recusado, em vez
     * de gravado e casado por aproximacao depois.
     */
    private List<ItemCardapioDto> resolverReceitas(List<ItemCardapioDto> itens) {
        return itens.stream().map(item -> {
            if (item.receitaId() != null) {
                var receita = receitas.findById(item.receitaId()).orElseThrow(
                    () -> new ReceitaDesconhecidaException(
                        "Receita %s não existe no catálogo".formatted(item.receitaId())));
                return new ItemCardapioDto(receita.getId(), receita.getNome(), item.quantidade());
            }
            var receita = receitas.findByNome(item.nome()).orElseThrow(
                () -> new ReceitaDesconhecidaException(
                    "Item \"%s\" não existe no catálogo de receitas".formatted(item.nome())));
            return new ItemCardapioDto(receita.getId(), receita.getNome(), item.quantidade());
        }).toList();
    }

    private String escreverItens(List<ItemCardapioDto> itens) {
        try {
            return objectMapper.writeValueAsString(itens);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Nao foi possivel serializar os itens do cardapio", exception);
        }
    }

    private List<ItemCardapioDto> lerItens(String itensJson) {
        try {
            return objectMapper.readValue(itensJson, LISTA_DE_ITENS);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Itens do cardapio estao em formato invalido", exception);
        }
    }
}
