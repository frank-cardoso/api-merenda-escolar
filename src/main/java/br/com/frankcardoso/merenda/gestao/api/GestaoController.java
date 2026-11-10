package br.com.frankcardoso.merenda.gestao.api;

import br.com.frankcardoso.merenda.gestao.application.ConsolidacaoConsumoService;
import br.com.frankcardoso.merenda.gestao.application.CardapioAnaliseService;
import java.util.List;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/gestao")
public class GestaoController {

    private final ConsolidacaoConsumoService service;
    private final CardapioAnaliseService cardapioAnaliseService;

    public GestaoController(ConsolidacaoConsumoService service, CardapioAnaliseService cardapioAnaliseService) {
        this.service = service;
        this.cardapioAnaliseService = cardapioAnaliseService;
    }

    @GetMapping("/consolidacoes")
    ConsolidacaoConsumoResponse consolidar(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
        @RequestParam UUID tipoRefeicaoId
    ) {
        return service.consolidar(data, tipoRefeicaoId);
    }

    @GetMapping("/cardapios-analise")
    List<CardapioAnaliseResponse> cardapiosParaAnalise(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
        @RequestParam UUID tipoRefeicaoId
    ) {
        return cardapioAnaliseService.listar(inicio, fim, tipoRefeicaoId);
    }
}
