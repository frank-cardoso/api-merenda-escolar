package br.com.frankcardoso.merenda.refeicao.application;

import br.com.frankcardoso.merenda.refeicao.api.TipoRefeicaoRequest;
import br.com.frankcardoso.merenda.refeicao.domain.GradeRefeicoes;
import br.com.frankcardoso.merenda.refeicao.domain.TipoRefeicao;
import br.com.frankcardoso.merenda.refeicao.infrastructure.TipoRefeicaoRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GradeRefeicoesService {

    private final TipoRefeicaoRepository repositorio;

    public GradeRefeicoesService(TipoRefeicaoRepository repositorio) {
        this.repositorio = repositorio;
    }

    public List<TipoRefeicao> listar() {
        return repositorio.findAllByOrderByOrdemAsc();
    }

    public TipoRefeicao buscar(UUID id) {
        return repositorio.findById(id).orElseThrow(() -> new TipoRefeicaoNaoEncontradoException(id));
    }

    public GradeRefeicoes grade() {
        return GradeRefeicoes.de(listar());
    }

    @Transactional
    public TipoRefeicao criar(TipoRefeicaoRequest request) {
        var nome = request.nome().strip();
        validar(null, nome, request);
        var nova = new TipoRefeicao(UUID.randomUUID(), nome, listar().size() + 1, request.inicio(), request.fim(),
            request.turnos());
        validarGrade(listar(), nova);
        var salva = repositorio.save(nova);
        renumerarPeloInicio();
        return salva;
    }

    @Transactional
    public TipoRefeicao atualizar(UUID id, TipoRefeicaoRequest request) {
        var refeicao = buscar(id);
        var nome = request.nome().strip();
        validar(id, nome, request);
        validarGrade(listar(), new TipoRefeicao(id, nome, refeicao.getOrdem(), request.inicio(), request.fim(),
            request.turnos()));
        refeicao.atualizar(nome, request.inicio(), request.fim(), request.turnos());
        renumerarPeloInicio();
        return refeicao;
    }

    private void validar(UUID id, String nome, TipoRefeicaoRequest request) {
        if (!request.inicio().isBefore(request.fim())) {
            throw new JanelaDeRefeicaoInvalidaException();
        }
        repositorio.findByNomeIgnoreCase(nome)
            .filter(existente -> !existente.getId().equals(id))
            .ifPresent(existente -> {
                throw new NomeDeRefeicaoDuplicadoException(nome);
            });
    }

    /** Valida a grade resultante com cópias, antes de tocar nas entidades gerenciadas. */
    private void validarGrade(List<TipoRefeicao> atuais, TipoRefeicao candidata) {
        var resultante = new ArrayList<>(atuais.stream()
            .filter(r -> !r.getId().equals(candidata.getId()))
            .map(r -> new TipoRefeicao(r.getId(), r.getNome(), r.getOrdem(), r.getInicio(), r.getFim(),
                r.getTurnos()))
            .toList());
        resultante.add(candidata);
        GradeRefeicoes.de(resultante);
    }

    /** Duas fases porque {@code ordem} é única e a troca de posições colidiria no meio do flush. */
    private void renumerarPeloInicio() {
        var ordenadas = repositorio.findAll().stream()
            .sorted(Comparator.comparing(TipoRefeicao::getInicio).thenComparing(TipoRefeicao::getNome))
            .toList();
        for (int i = 0; i < ordenadas.size(); i++) {
            ordenadas.get(i).definirOrdem(-(i + 1));
        }
        repositorio.flush();
        for (int i = 0; i < ordenadas.size(); i++) {
            ordenadas.get(i).definirOrdem(i + 1);
        }
    }
}
