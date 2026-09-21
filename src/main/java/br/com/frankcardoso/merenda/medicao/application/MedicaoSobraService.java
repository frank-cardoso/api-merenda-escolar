package br.com.frankcardoso.merenda.medicao.application;

import br.com.frankcardoso.merenda.catalogo.infrastructure.ReceitaRepository;
import br.com.frankcardoso.merenda.medicao.api.MedicaoSobraRequest;
import br.com.frankcardoso.merenda.medicao.api.MedicaoSobraResponse;
import br.com.frankcardoso.merenda.medicao.domain.MedicaoSobra;
import br.com.frankcardoso.merenda.medicao.domain.MedicaoSobraInconsistenteException;
import br.com.frankcardoso.merenda.medicao.infrastructure.MedicaoSobraRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicaoSobraService {

    public static final String ORIGEM_LANCADA = "LANCADA";

    private final MedicaoSobraRepository repository;
    private final ReceitaRepository receitas;
    private final Clock clock;
    private final int escolaDoPrototipo;

    public MedicaoSobraService(MedicaoSobraRepository repository, ReceitaRepository receitas,
        Clock clock, @Value("${merenda.medicao.escola-id:1}") int escolaDoPrototipo) {
        this.repository = repository;
        this.receitas = receitas;
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
        if (!receitas.existsById(request.receitaId())) {
            throw new MedicaoSobraInconsistenteException(
                "Receita %s não existe no catálogo".formatted(request.receitaId()));
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
}
