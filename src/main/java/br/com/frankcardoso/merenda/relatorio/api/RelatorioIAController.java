package br.com.frankcardoso.merenda.relatorio.api;

import br.com.frankcardoso.merenda.relatorio.application.RelatorioIAService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import java.time.LocalDate;
import java.util.List;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/relatorios-ia")
public class RelatorioIAController {

    private final RelatorioIAService service;

    public RelatorioIAController(RelatorioIAService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<CriarRelatorioIAResponse> criar(@Valid @RequestBody CriarRelatorioIARequest request) {
        var response = service.criar(request);
        return ResponseEntity.accepted()
            .location(URI.create(response.statusUrl()))
            .body(response);
    }

    @GetMapping("/{id}")
    RelatorioIAResponse buscar(@PathVariable UUID id) {
        return service.buscar(id);
    }

    @GetMapping
    List<RelatorioResumoResponse> listar(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
        @RequestParam Turno turno) {
        return service.listar(data, turno);
    }
}
