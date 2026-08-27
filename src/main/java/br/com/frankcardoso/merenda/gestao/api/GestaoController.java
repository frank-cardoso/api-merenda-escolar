package br.com.frankcardoso.merenda.gestao.api;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.gestao.application.ConsolidacaoConsumoService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gestao")
public class GestaoController {

    private final ConsolidacaoConsumoService service;

    public GestaoController(ConsolidacaoConsumoService service) {
        this.service = service;
    }

    @GetMapping("/consolidacoes")
    ConsolidacaoConsumoResponse consolidar(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
        @RequestParam Turno turno
    ) {
        return service.consolidar(data, turno);
    }
}
