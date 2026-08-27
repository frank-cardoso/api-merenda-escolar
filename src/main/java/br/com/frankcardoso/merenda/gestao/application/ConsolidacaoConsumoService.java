package br.com.frankcardoso.merenda.gestao.application;

import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.gestao.api.ConsolidacaoConsumoResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsolidacaoConsumoService {

    private final CardapioRepository cardapioRepository;
    private final AuditoriaConsumoRepository auditoriaRepository;

    public ConsolidacaoConsumoService(CardapioRepository cardapioRepository,
                                      AuditoriaConsumoRepository auditoriaRepository) {
        this.cardapioRepository = cardapioRepository;
        this.auditoriaRepository = auditoriaRepository;
    }

    @Transactional(readOnly = true)
    public ConsolidacaoConsumoResponse consolidar(LocalDate data, Turno turno) {
        var cardapio = cardapioRepository.findByDataAndTurnoAndAtivoTrue(data, turno)
            .orElseThrow(() -> new CardapioNaoEncontradoException(data, turno));

        long autorizados = auditoriaRepository.countByDataOperacionalAndTurnoAndResultado(
            data, turno, ResultadoConsumo.AUTORIZADO);
        long bloqueados = auditoriaRepository.countByDataOperacionalAndTurnoAndResultado(
            data, turno, ResultadoConsumo.BLOQUEADO);
        long sobra = Math.max(0, cardapio.getQuantidadePlanejada() - autorizados);
        BigDecimal taxa = calcularTaxa(autorizados, cardapio.getQuantidadePlanejada());

        return new ConsolidacaoConsumoResponse(
            data,
            turno,
            cardapio.getId(),
            cardapio.getNomeRefeicao(),
            cardapio.getQuantidadePlanejada(),
            autorizados,
            bloqueados,
            taxa,
            sobra
        );
    }

    private BigDecimal calcularTaxa(long consumos, int quantidadePlanejada) {
        if (quantidadePlanejada == 0) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(consumos)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(quantidadePlanejada), 2, RoundingMode.HALF_UP);
    }
}
