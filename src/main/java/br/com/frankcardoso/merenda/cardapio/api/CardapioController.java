package br.com.frankcardoso.merenda.cardapio.api;

import br.com.frankcardoso.merenda.cardapio.application.CardapioService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cardapios")
public class CardapioController {

    private final CardapioService service;

    public CardapioController(CardapioService service) {
        this.service = service;
    }

    @GetMapping
    List<CardapioResponse> listar() {
        return service.listar();
    }

    @PostMapping
    ResponseEntity<CardapioResponse> criar(@Valid @RequestBody CardapioRequest request) {
        var response = service.criar(request);
        return ResponseEntity.created(URI.create("/api/v1/cardapios/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    CardapioResponse atualizar(@PathVariable UUID id, @Valid @RequestBody CardapioRequest request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> desativar(@PathVariable UUID id) {
        service.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
