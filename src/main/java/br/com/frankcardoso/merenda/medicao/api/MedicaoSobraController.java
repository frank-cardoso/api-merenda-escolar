package br.com.frankcardoso.merenda.medicao.api;

import br.com.frankcardoso.merenda.medicao.application.MedicaoSobraService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.List;
import br.com.frankcardoso.merenda.fila.domain.Turno;

@RestController
@RequestMapping("/api/v1/medicoes-sobra")
public class MedicaoSobraController {

    private final MedicaoSobraService service;

    public MedicaoSobraController(MedicaoSobraService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<MedicaoSobraResponse> registrar(@Valid @RequestBody MedicaoSobraRequest request) {
        return ResponseEntity.ok(service.registrar(request));
    }

    @PostMapping("/fechamento")
    List<MedicaoSobraResponse> registrarFechamento(
        @Valid @RequestBody FechamentoSobraRequest request) {
        return service.registrarFechamento(request.medicoes());
    }

    @GetMapping
    List<MedicaoSobraResponse> listar(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
        @RequestParam Turno turno) {
        return service.listar(data, turno);
    }
}
