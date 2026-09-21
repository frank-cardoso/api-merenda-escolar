package br.com.frankcardoso.merenda.shared.web;

import br.com.frankcardoso.merenda.cardapio.application.CardapioDataPassadaException;
import br.com.frankcardoso.merenda.cardapio.application.CardapioDuplicadoException;
import br.com.frankcardoso.merenda.cardapio.application.CardapioNaoEncontradoPorIdException;
import br.com.frankcardoso.merenda.cardapio.application.ReceitaDesconhecidaException;
import br.com.frankcardoso.merenda.gestao.application.CardapioNaoEncontradoException;
import br.com.frankcardoso.merenda.medicao.domain.MedicaoSobraInconsistenteException;
import br.com.frankcardoso.merenda.relatorio.application.RelatorioNaoEncontradoException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler({
        CardapioNaoEncontradoException.class,
        CardapioNaoEncontradoPorIdException.class,
        RelatorioNaoEncontradoException.class
    })
    ProblemDetail tratarNaoEncontrado(RuntimeException exception, HttpServletRequest request) {
        return problema(HttpStatus.NOT_FOUND, "Recurso nao encontrado", exception.getMessage(), request);
    }

    @ExceptionHandler(CardapioDuplicadoException.class)
    ProblemDetail tratarConflito(CardapioDuplicadoException exception, HttpServletRequest request) {
        return problema(HttpStatus.CONFLICT, "Conflito", exception.getMessage(), request);
    }

    @ExceptionHandler({CardapioDataPassadaException.class, MedicaoSobraInconsistenteException.class,
        ReceitaDesconhecidaException.class})
    ProblemDetail tratarEntradaInvalida(RuntimeException exception, HttpServletRequest request) {
        return problema(HttpStatus.BAD_REQUEST, "Requisicao invalida", exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail tratarValidacao(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String detalhe = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .orElse("Requisicao invalida");
        return problema(HttpStatus.BAD_REQUEST, "Requisicao invalida", detalhe, request);
    }

    private ProblemDetail problema(HttpStatus status, String titulo, String detalhe, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, detalhe);
        problem.setTitle(titulo);
        problem.setType(URI.create("https://merenda.local/problems/" + status.value()));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
