package br.com.frankcardoso.merenda.gestao.api;

import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.gestao.application.IndicadoresLogisticosService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/gestao/indicadores")
public class IndicadoresController {
    private final IndicadoresLogisticosService service;

    public IndicadoresController(IndicadoresLogisticosService service) { this.service = service; }

    @GetMapping
    public IndicadoresLogisticos consultar(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
        @RequestParam Turno turno,
        @RequestParam(required = false) List<UUID> receitaIds,
        @RequestParam(required = false) List<LocalDate> datas) {
        return service.calcular(data, turno, receitaIds == null ? List.of() : receitaIds,
            datas == null ? List.of() : datas);
    }
}
