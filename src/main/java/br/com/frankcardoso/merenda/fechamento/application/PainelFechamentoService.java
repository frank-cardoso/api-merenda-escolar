package br.com.frankcardoso.merenda.fechamento.application;

import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.fechamento.api.FechamentoPendenteResponse;
import br.com.frankcardoso.merenda.fechamento.api.RefeicaoDoDiaResponse;
import br.com.frankcardoso.merenda.fechamento.domain.SituacaoFechamento;
import br.com.frankcardoso.merenda.medicao.domain.MedicaoSobra;
import br.com.frankcardoso.merenda.medicao.infrastructure.MedicaoSobraRepository;
import br.com.frankcardoso.merenda.refeicao.application.GradeRefeicoesService;
import br.com.frankcardoso.merenda.refeicao.domain.TipoRefeicao;
import br.com.frankcardoso.merenda.shared.config.ApplicationConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PainelFechamentoService {

    private static final int ESCOLA_ID = 1;

    private final GradeRefeicoesService grade;
    private final CardapioRepository cardapios;
    private final MedicaoSobraRepository medicoes;
    private final AuditoriaConsumoRepository auditorias;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PainelFechamentoService(GradeRefeicoesService grade, CardapioRepository cardapios,
                                   MedicaoSobraRepository medicoes, AuditoriaConsumoRepository auditorias,
                                   ObjectMapper objectMapper, Clock clock) {
        this.grade = grade;
        this.cardapios = cardapios;
        this.medicoes = medicoes;
        this.auditorias = auditorias;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public List<RefeicaoDoDiaResponse> doDia(LocalDate data) {
        return doDia(data, agoraOperacional());
    }

    private List<RefeicaoDoDiaResponse> doDia(LocalDate data, ZonedDateTime agora) {
        return grade.listar().stream().map(refeicao -> montar(data, refeicao, agora)).toList();
    }

    private ZonedDateTime agoraOperacional() {
        return clock.instant().atZone(ApplicationConfig.ZONA_OPERACIONAL);
    }

    /** Pendências dos {@code dias} dias que terminam em {@code ate} (inclusive), por data e ordem da grade. */
    public List<FechamentoPendenteResponse> pendencias(LocalDate ate, int dias) {
        var resultado = new ArrayList<FechamentoPendenteResponse>();
        var agora = agoraOperacional();
        for (int voltar = dias - 1; voltar >= 0; voltar--) {
            var data = ate.minusDays(voltar);
            for (var linha : doDia(data, agora)) {
                if (linha.situacao() != SituacaoFechamento.PENDENTE) continue;
                resultado.add(new FechamentoPendenteResponse(data, linha.tipoRefeicaoId(), linha.refeicao(),
                    linha.itensCardapio(), linha.itensMedidos()));
            }
        }
        return resultado;
    }

    private RefeicaoDoDiaResponse montar(LocalDate data, TipoRefeicao refeicao, ZonedDateTime agora) {
        var cardapio = cardapios.findByDataAndTipoRefeicaoIdAndAtivoTrue(data, refeicao.getId()).orElse(null);
        Set<UUID> receitas = cardapio == null ? Set.of() : receitasDo(cardapio);
        List<MedicaoSobra> medidas = cardapio == null ? List.of() : medicoes
            .findAllByDataAndTipoRefeicaoIdAndEscolaIdOrderByReceitaId(data, refeicao.getId(), ESCOLA_ID)
            .stream().filter(medicao -> receitas.contains(medicao.getReceitaId())).toList();
        int itensMedidos = medidas.size();
        boolean completa = !receitas.isEmpty() && itensMedidos >= receitas.size();
        var situacao = SituacaoFechamento.de(cardapio != null, completa, data, refeicao.getInicio(),
            refeicao.getFim(), agora.toLocalDate(), agora.toLocalTime());
        Instant fechadoEm = situacao == SituacaoFechamento.FECHADA
            ? medidas.stream().map(MedicaoSobra::getRegistradoEm).max(Instant::compareTo).orElse(null)
            : null;
        long consumos = auditorias.countByDataOperacionalAndTipoRefeicaoIdAndResultado(
            data, refeicao.getId(), ResultadoConsumo.AUTORIZADO);
        return new RefeicaoDoDiaResponse(refeicao.getId(), refeicao.getNome(), refeicao.getOrdem(),
            refeicao.getInicio(), refeicao.getFim(), situacao,
            cardapio == null ? null : cardapio.getId(), receitas.size(), itensMedidos, consumos, fechadoEm);
    }

    private Set<UUID> receitasDo(Cardapio cardapio) {
        try {
            return objectMapper.readTree(cardapio.getItensJson()).findValues("receitaId").stream()
                .filter(JsonNode::isTextual).map(JsonNode::asText).filter(texto -> !texto.isBlank()).map(UUID::fromString)
                .collect(Collectors.toSet());
        } catch (Exception exception) {
            throw new IllegalStateException("Itens do cardápio %s ilegíveis".formatted(cardapio.getId()), exception);
        }
    }
}
