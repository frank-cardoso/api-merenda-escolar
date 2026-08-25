package br.com.frankcardoso.merenda.fila.api;

import br.com.frankcardoso.merenda.fila.application.ValidacaoConsumoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fila")
public class FilaController {

    private final ValidacaoConsumoService service;

    public FilaController(ValidacaoConsumoService service) {
        this.service = service;
    }

    @PostMapping("/validacoes")
    ResponseEntity<ValidarConsumoResponse> validar(@Valid @RequestBody ValidarConsumoRequest request) {
        return ResponseEntity.ok(service.validar(request));
    }
}
