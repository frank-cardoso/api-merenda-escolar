package br.com.frankcardoso.merenda.turma.api;

import br.com.frankcardoso.merenda.turma.application.TurmaService;
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
@RequestMapping("/api/v1/turmas")
public class TurmaController {

    private final TurmaService service;

    public TurmaController(TurmaService service) {
        this.service = service;
    }

    @GetMapping
    List<TurmaResponse> listar() {
        return service.listar().stream().map(TurmaResponse::de).toList();
    }

    @GetMapping("/{id}")
    TurmaResponse buscar(@PathVariable UUID id) {
        return TurmaResponse.de(service.buscar(id));
    }

    @PostMapping
    ResponseEntity<TurmaResponse> criar(@Valid @RequestBody TurmaRequest request) {
        var turma = service.criar(request.codigo(), request.descricao(), request.turno(),
            request.quantidadeMaximaAlunos());
        return ResponseEntity.created(URI.create("/api/v1/turmas/" + turma.getId())).body(TurmaResponse.de(turma));
    }

    @PutMapping("/{id}")
    TurmaResponse atualizar(@PathVariable UUID id, @Valid @RequestBody TurmaRequest request) {
        return TurmaResponse.de(service.atualizar(id, request.codigo(), request.descricao(), request.turno(),
            request.quantidadeMaximaAlunos()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> excluir(@PathVariable UUID id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
