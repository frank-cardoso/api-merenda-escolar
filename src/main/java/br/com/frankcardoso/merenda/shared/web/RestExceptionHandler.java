package br.com.frankcardoso.merenda.shared.web;

import br.com.frankcardoso.merenda.gestao.application.CardapioNaoEncontradoException;
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

    @ExceptionHandler({CardapioNaoEncontradoException.class, RelatorioNaoEncontradoException.class})
    ProblemDetail tratarNaoEncontrado(RuntimeException exception, HttpServletRequest request) {
        return problema(HttpStatus.NOT_FOUND, "Recurso nao encontrado", exception.getMessage(), request);
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
