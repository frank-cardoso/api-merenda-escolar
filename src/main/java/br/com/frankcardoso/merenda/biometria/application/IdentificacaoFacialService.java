package br.com.frankcardoso.merenda.biometria.application;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.biometria.application.IdentificacaoFacial.Resultado;
import br.com.frankcardoso.merenda.biometria.application.port.ReconhecimentoFacialPort;
import br.com.frankcardoso.merenda.biometria.application.port.ReconhecimentoFacialPort.Candidato;
import br.com.frankcardoso.merenda.biometria.application.port.ReconhecimentoFacialPort.Decisao;
import br.com.frankcardoso.merenda.biometria.application.port.ReconhecimentoFacialPort.ResultadoIdentificacao;
import br.com.frankcardoso.merenda.biometria.domain.ReconhecimentoFacialIndisponivelException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Identificacao 1:N: compara a foto com os rostos cadastrados dos alunos ativos. */
@Service
public class IdentificacaoFacialService {

    private static final Logger LOG = LoggerFactory.getLogger(IdentificacaoFacialService.class);

    private final ReconhecimentoFacialPort reconhecimentoFacial;
    private final AlunoRepository alunoRepository;
    private final ObjectMapper objectMapper;

    public IdentificacaoFacialService(ReconhecimentoFacialPort reconhecimentoFacial,
                                      AlunoRepository alunoRepository, ObjectMapper objectMapper) {
        this.reconhecimentoFacial = reconhecimentoFacial;
        this.alunoRepository = alunoRepository;
        this.objectMapper = objectMapper;
    }

    public IdentificacaoFacial identificar(String fotoBase64) {
        List<Usavel> usaveis = carregarCandidatos(alunoRepository.findByAtivoTrueAndRostoEmbeddingJsonIsNotNull());
        if (usaveis.isEmpty()) return new IdentificacaoFacial(Resultado.SEM_CANDIDATOS, null);

        ResultadoIdentificacao resultado = consultar(fotoBase64, usaveis);
        return switch (resultado.decisao()) {
            case RECONHECIDO -> new IdentificacaoFacial(Resultado.RECONHECIDO, maisProximo(usaveis, resultado));
            case AMBIGUO -> new IdentificacaoFacial(Resultado.AMBIGUO, maisProximo(usaveis, resultado));
            case NAO_RECONHECIDO -> new IdentificacaoFacial(Resultado.NAO_RECONHECIDO, null);
        };
    }

    /** Usado no cadastro: AMBIGUO tambem conta, pois a distancia ja esta abaixo do limiar. */
    public Optional<Aluno> alunoComMesmoRosto(String fotoBase64, UUID alunoIgnorado) {
        List<Usavel> usaveis = carregarCandidatos(
            alunoRepository.findByAtivoTrueAndRostoEmbeddingJsonIsNotNull().stream()
                .filter(aluno -> !aluno.getId().equals(alunoIgnorado))
                .toList());
        if (usaveis.isEmpty()) return Optional.empty();

        ResultadoIdentificacao resultado = consultar(fotoBase64, usaveis);
        if (resultado.decisao() == Decisao.NAO_RECONHECIDO) return Optional.empty();
        return Optional.of(maisProximo(usaveis, resultado));
    }

    /** Candidato com embedding ilegivel e pulado: um cadastro corrompido nao pode derrubar a leitura de todos. */
    private List<Usavel> carregarCandidatos(List<Aluno> alunos) {
        List<Usavel> usaveis = new ArrayList<>();
        for (Aluno aluno : alunos) {
            List<Float> embedding = lerEmbedding(aluno);
            if (embedding == null || embedding.isEmpty()) {
                LOG.warn("Embedding facial ilegivel ou vazio, aluno {} ignorado na identificacao", aluno.getId());
                continue;
            }
            usaveis.add(new Usavel(aluno, new Candidato(aluno.getId(), embedding)));
        }
        return usaveis;
    }

    private ResultadoIdentificacao consultar(String fotoBase64, List<Usavel> usaveis) {
        List<Candidato> candidatos = usaveis.stream().map(Usavel::candidato).toList();
        ResultadoIdentificacao resultado = reconhecimentoFacial.identificar(fotoBase64, candidatos);
        if (resultado.candidatosIgnorados() > 0) {
            LOG.warn("{} de {} candidatos faciais ignorados por embedding de dimensao incompativel",
                resultado.candidatosIgnorados(), candidatos.size());
        }
        return resultado;
    }

    private Aluno maisProximo(List<Usavel> usaveis, ResultadoIdentificacao resultado) {
        Map<UUID, Aluno> porId = usaveis.stream().collect(Collectors.toMap(u -> u.aluno().getId(), Usavel::aluno));
        Aluno aluno = porId.get(resultado.melhorCandidatoId());
        if (aluno == null) {
            throw new ReconhecimentoFacialIndisponivelException(
                "Reconhecimento facial devolveu candidato desconhecido: " + resultado.melhorCandidatoId(), null);
        }
        return aluno;
    }

    private List<Float> lerEmbedding(Aluno aluno) {
        try {
            return objectMapper.readValue(aluno.getRostoEmbeddingJson(), new TypeReference<List<Float>>() { });
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return null;
        }
    }

    private record Usavel(Aluno aluno, Candidato candidato) {
    }
}
