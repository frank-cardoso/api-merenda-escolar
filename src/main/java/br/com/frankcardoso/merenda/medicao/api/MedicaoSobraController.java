package br.com.frankcardoso.merenda.medicao.api;

import br.com.frankcardoso.merenda.medicao.application.MedicaoSobraService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
