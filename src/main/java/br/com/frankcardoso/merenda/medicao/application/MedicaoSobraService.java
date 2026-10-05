package br.com.frankcardoso.merenda.medicao.application;

import br.com.frankcardoso.merenda.catalogo.infrastructure.ReceitaRepository;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.medicao.api.MedicaoSobraRequest;
import br.com.frankcardoso.merenda.medicao.api.MedicaoSobraResponse;
import br.com.frankcardoso.merenda.medicao.domain.MedicaoSobra;
import br.com.frankcardoso.merenda.medicao.domain.MedicaoSobraInconsistenteException;
import br.com.frankcardoso.merenda.medicao.infrastructure.MedicaoSobraRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicaoSobraService {

    public static final String ORIGEM_LANCADA = "LANCADA";

    private final MedicaoSobraRepository repository;
    private final ReceitaRepository receitas;
    private final CardapioRepository cardapios;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int escolaDoPrototipo;

    public MedicaoSobraService(MedicaoSobraRepository repository, ReceitaRepository receitas,
        CardapioRepository cardapios, ObjectMapper objectMapper, Clock clock,
        @Value("${merenda.medicao.escola-id:1}") int escolaDoPrototipo) {
        this.repository = repository;
        this.receitas = receitas;
        this.cardapios = cardapios;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.escolaDoPrototipo = escolaDoPrototipo;
    }

    /**
     * Relancar o mesmo item no mesmo dia/turno sobrescreve a medicao anterior.
     *
     * Medicao e observacao, nao evento: se a cozinha corrige o numero depois de pesar de novo, o
     * certo e o valor corrigido — nao duas linhas somando o dobro de porcoes.
     */
    @Transactional
    public MedicaoSobraResponse registrar(MedicaoSobraRequest request) {
        return registrarInterno(request);
    }

    @Transactional
    public List<MedicaoSobraResponse> registrarFechamento(List<MedicaoSobraRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new MedicaoSobraInconsistenteException("Fechamento deve conter pelo menos um item");
        }
        var data = requests.get(0).data();
        var turno = requests.get(0).turno();
        if (requests.stream().anyMatch(request -> !data.equals(request.data())
            || turno != request.turno())) {
            throw new MedicaoSobraInconsistenteException(
                "Todos os itens do fechamento devem usar a mesma data e turno");
        }
        return requests.stream().map(this::registrarInterno).toList();
    }

    private MedicaoSobraResponse registrarInterno(MedicaoSobraRequest request) {
        if (!receitas.existsById(request.receitaId())) {
            throw new MedicaoSobraInconsistenteException(
                "Receita %s não existe no catálogo".formatted(request.receitaId()));
        }
        if (!pertenceAoCardapio(request)) {
            throw new MedicaoSobraInconsistenteException(
                "Receita %s não pertence ao cardápio de %s / %s"
                    .formatted(request.receitaId(), request.data(), request.turno()));
        }

        var existente = repository.findByDataAndTurnoAndEscolaIdAndReceitaId(
            request.data(), request.turno(), escolaDoPrototipo, request.receitaId());

        var medicao = new MedicaoSobra(
            existente.map(MedicaoSobra::getId).orElseGet(UUID::randomUUID),
            request.data(),
            request.turno(),
            escolaDoPrototipo,
            request.receitaId(),
            request.porcoesPreparadas(),
            request.porcoesServidas(),
            request.sobraNaoDistribuida(),
            request.restoNoPrato(),
            ORIGEM_LANCADA,
            Instant.now(clock)
        );

        return MedicaoSobraResponse.de(repository.save(medicao));
    }

    private boolean pertenceAoCardapio(MedicaoSobraRequest request) {
        var cardapio = cardapios.findByDataAndTurnoAndAtivoTrue(request.data(), request.turno())
            .orElse(null);
        if (cardapio == null) return false;
        try {
            return objectMapper.readTree(cardapio.getItensJson()).findValues("receitaId").stream()
                .map(node -> node.isNull() ? null : node.asText())
                .anyMatch(request.receitaId().toString()::equals);
        } catch (Exception exception) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public List<MedicaoSobraResponse> listar(LocalDate data, Turno turno) {
        return repository.findAllByDataAndTurnoAndEscolaIdOrderByReceitaId(data, turno,
            escolaDoPrototipo).stream().map(MedicaoSobraResponse::de).toList();
    }
}
