package br.com.frankcardoso.merenda.fechamento.api;

import br.com.frankcardoso.merenda.fechamento.application.PainelFechamentoService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fechamentos")
public class FechamentoController {

    private final PainelFechamentoService service;

    public FechamentoController(PainelFechamentoService service) {
        this.service = service;
    }

    @GetMapping("/dia")
    List<RefeicaoDoDiaResponse> doDia(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return service.doDia(data);
    }

    @GetMapping("/pendencias")
    List<FechamentoPendenteResponse> pendencias(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
        @RequestParam(defaultValue = "7") @Min(1) @Max(31) int dias) {
        return service.pendencias(ate, dias);
    }
}
