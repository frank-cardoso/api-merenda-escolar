package br.com.frankcardoso.merenda.fila.application;

import br.com.frankcardoso.merenda.refeicao.application.GradeRefeicoesService;
import br.com.frankcardoso.merenda.refeicao.domain.GradeRefeicoes;
import br.com.frankcardoso.merenda.refeicao.domain.GradeRefeicoesInvalidaException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Carrega a grade para a fila; grade inválida vira ausência, nunca erro. Chamar fora de transação:
 * a exceção atravessando o proxy transacional do serviço marcaria a transação como rollback-only.
 */
@Component
public class RefeicaoResolver {

    private static final Logger LOG = LoggerFactory.getLogger(RefeicaoResolver.class);

    private final GradeRefeicoesService gradeRefeicoesService;

    public RefeicaoResolver(GradeRefeicoesService gradeRefeicoesService) {
        this.gradeRefeicoesService = gradeRefeicoesService;
    }

    public Optional<GradeRefeicoes> carregarGrade() {
        try {
            return Optional.of(gradeRefeicoesService.grade());
        } catch (GradeRefeicoesInvalidaException exception) {
            LOG.warn("Grade de refeições inválida; a fila bloqueia até ser corrigida: {}", exception.getMessage());
            return Optional.empty();
        }
    }
}
