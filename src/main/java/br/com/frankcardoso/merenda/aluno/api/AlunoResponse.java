package br.com.frankcardoso.merenda.aluno.api;

import br.com.frankcardoso.merenda.aluno.application.AlunoService.AlunoDetalhe;
import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.turma.domain.Enturmacao;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AlunoResponse(
    UUID id,
    String codigoPublico,
    String matricula,
    String nome,
    boolean ativo,
    String codigoOrigem,
    VinculoResponse turmaVigente,
    List<VinculoResponse> historicoVinculos
) {

    public record VinculoResponse(UUID id, UUID turmaId, String turmaCodigo, String turmaDescricao,
                                  LocalDate dataInicio, LocalDate dataFim) {

        static VinculoResponse de(Enturmacao e) {
            var turma = e.getTurma();
            return new VinculoResponse(e.getId(), turma.getId(), turma.getCodigo(), turma.getDescricao(),
                e.getDataInicio(), e.getDataFim());
        }
    }

    static AlunoResponse de(AlunoDetalhe detalhe) {
        Aluno a = detalhe.aluno();
        return new AlunoResponse(a.getId(), a.getCodigoPublico(), a.getMatricula(), a.getNome(), a.isAtivo(), a.getCodigoOrigem(),
            detalhe.vigente().map(VinculoResponse::de).orElse(null),
            detalhe.historico().stream().map(VinculoResponse::de).toList());
    }

    /** Listagem: so a turma vigente, sem o historico. */
    static AlunoResponse resumo(Aluno a) {
        return new AlunoResponse(a.getId(), a.getCodigoPublico(), a.getMatricula(), a.getNome(), a.isAtivo(), a.getCodigoOrigem(),
            null, null);
    }
}
