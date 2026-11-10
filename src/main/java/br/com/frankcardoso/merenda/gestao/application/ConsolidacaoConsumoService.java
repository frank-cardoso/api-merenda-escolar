package br.com.frankcardoso.merenda.gestao.application;

import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.gestao.api.ConsolidacaoConsumoResponse;
import br.com.frankcardoso.merenda.refeicao.application.GradeRefeicoesService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsolidacaoConsumoService {

    private static final TypeReference<List<ItemCardapio>> LISTA_DE_ITENS = new TypeReference<>() {
    };

    private final CardapioRepository cardapioRepository;
    private final AuditoriaConsumoRepository auditoriaRepository;
    private final GradeRefeicoesService grade;
    private final ObjectMapper objectMapper;

    public ConsolidacaoConsumoService(CardapioRepository cardapioRepository,
                                      AuditoriaConsumoRepository auditoriaRepository,
                                      GradeRefeicoesService grade,
                                      ObjectMapper objectMapper) {
        this.cardapioRepository = cardapioRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.grade = grade;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ConsolidacaoConsumoResponse consolidar(LocalDate data, UUID tipoRefeicaoId) {
        var refeicao = grade.buscar(tipoRefeicaoId);
        var cardapio = cardapioRepository.findByDataAndTipoRefeicaoIdAndAtivoTrue(data, tipoRefeicaoId)
            .orElseThrow(() -> new CardapioNaoEncontradoException(data, refeicao.getNome()));

        long autorizados = auditoriaRepository.countByDataOperacionalAndTipoRefeicaoIdAndResultado(
            data, tipoRefeicaoId, ResultadoConsumo.AUTORIZADO);
        long bloqueados = auditoriaRepository.countByDataOperacionalAndTipoRefeicaoIdAndResultado(
            data, tipoRefeicaoId, ResultadoConsumo.BLOQUEADO);
        long sobra = Math.max(0, cardapio.getQuantidadePlanejada() - autorizados);
        BigDecimal taxa = calcularTaxa(autorizados, cardapio.getQuantidadePlanejada());

        var itens = lerItens(cardapio.getItensJson());

        return new ConsolidacaoConsumoResponse(
            data,
            tipoRefeicaoId,
            refeicao.getNome(),
            cardapio.getId(),
            cardapio.getNomeRefeicao(),
            itens.stream().map(ItemCardapio::nome).toList(),
            itens.stream().map(ItemCardapio::receitaId).filter(Objects::nonNull).toList(),
            cardapio.getQuantidadePlanejada(),
            autorizados,
            bloqueados,
            taxa,
            sobra
        );
    }

    /**
     * Extrai os itens do cardapio. A quantidade por item (ex.: "10 kg") e informacao de compra,
     * nao ajuda a analise de aceitacao e so gastaria espaco no prompt.
     */
    private List<ItemCardapio> lerItens(String itensJson) {
        if (itensJson == null || itensJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(itensJson, LISTA_DE_ITENS).stream()
                .filter(item -> item.nome() != null && !item.nome().isBlank())
                .toList();
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private record ItemCardapio(UUID receitaId, String nome, String quantidade) {
    }

    private BigDecimal calcularTaxa(long consumos, int quantidadePlanejada) {
        if (quantidadePlanejada == 0) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(consumos)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(quantidadePlanejada), 2, RoundingMode.HALF_UP);
    }
}
