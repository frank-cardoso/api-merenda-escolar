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
import br.com.frankcardoso.merenda.refeicao.application.GradeRefeicoesService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicaoSobraService {

    public static final String ORIGEM_LANCADA = "LANCADA";

    private final MedicaoSobraRepository repository;
    private final ReceitaRepository receitas;
    private final CardapioRepository cardapios;
    private final GradeRefeicoesService grade;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int escolaDoPrototipo;

    public MedicaoSobraService(MedicaoSobraRepository repository, ReceitaRepository receitas,
        CardapioRepository cardapios, GradeRefeicoesService grade, ObjectMapper objectMapper, Clock clock,
        @Value("${merenda.medicao.escola-id:1}") int escolaDoPrototipo) {
        this.repository = repository;
        this.receitas = receitas;
        this.cardapios = cardapios;
        this.grade = grade;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.escolaDoPrototipo = escolaDoPrototipo;
    }

    /**
     * Relancar o mesmo item na mesma refeição do dia sobrescreve a medicao anterior.
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
        var tipoRefeicaoId = requests.get(0).tipoRefeicaoId();
        if (requests.stream().anyMatch(request -> !data.equals(request.data())
            || !tipoRefeicaoId.equals(request.tipoRefeicaoId()))) {
            throw new MedicaoSobraInconsistenteException(
                "Todos os itens do fechamento devem usar a mesma data e refeição");
        }
        return requests.stream().map(this::registrarInterno).toList();
    }

    private MedicaoSobraResponse registrarInterno(MedicaoSobraRequest request) {
        var refeicao = grade.buscar(request.tipoRefeicaoId());
        if (!receitas.existsById(request.receitaId())) {
            throw new MedicaoSobraInconsistenteException(
                "Receita %s não existe no catálogo".formatted(request.receitaId()));
        }
        if (!pertenceAoCardapio(request)) {
            throw new MedicaoSobraInconsistenteException(
                "Receita %s não pertence ao cardápio de %s (%s)"
                    .formatted(request.receitaId(), request.data(), refeicao.getNome()));
        }

        var existente = repository.findByDataAndTipoRefeicaoIdAndEscolaIdAndReceitaId(
            request.data(), request.tipoRefeicaoId(), escolaDoPrototipo, request.receitaId());

        var medicao = new MedicaoSobra(
            existente.map(MedicaoSobra::getId).orElseGet(UUID::randomUUID),
            request.data(),
            request.tipoRefeicaoId(),
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
        var cardapio = cardapios.findByDataAndTipoRefeicaoIdAndAtivoTrue(request.data(), request.tipoRefeicaoId())
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
    public List<MedicaoSobraResponse> listar(LocalDate data, UUID tipoRefeicaoId) {
        grade.buscar(tipoRefeicaoId);
        return repository.findAllByDataAndTipoRefeicaoIdAndEscolaIdOrderByReceitaId(data,
            tipoRefeicaoId, escolaDoPrototipo).stream().map(MedicaoSobraResponse::de).toList();
    }
}
