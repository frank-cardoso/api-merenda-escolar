package br.com.frankcardoso.merenda.refeicao.api;

import br.com.frankcardoso.merenda.refeicao.application.GradeRefeicoesService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tipos-refeicao")
public class TipoRefeicaoController {

    private final GradeRefeicoesService service;

    public TipoRefeicaoController(GradeRefeicoesService service) {
        this.service = service;
    }

    @GetMapping
    List<TipoRefeicaoResponse> listar() {
        return service.listar().stream().map(TipoRefeicaoResponse::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TipoRefeicaoResponse criar(@RequestBody @Valid TipoRefeicaoRequest request) {
        return TipoRefeicaoResponse.de(service.criar(request));
    }

    @PutMapping("/{id}")
    TipoRefeicaoResponse atualizar(@PathVariable UUID id, @RequestBody @Valid TipoRefeicaoRequest request) {
        return TipoRefeicaoResponse.de(service.atualizar(id, request));
    }
}
