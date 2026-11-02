package br.com.frankcardoso.merenda.aluno.api;

import br.com.frankcardoso.merenda.aluno.application.AlunoService;
import br.com.frankcardoso.merenda.shared.web.DadosInvalidosException;
import br.com.frankcardoso.merenda.turma.application.EnturmacaoService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alunos")
public class AlunoController {

    private final AlunoService service;
    private final EnturmacaoService enturmacaoService;

    public AlunoController(AlunoService service, EnturmacaoService enturmacaoService) {
        this.service = service;
        this.enturmacaoService = enturmacaoService;
    }

    @GetMapping
    List<AlunoResponse> listar() {
        return service.listar().stream().map(AlunoResponse::resumo).toList();
    }

    @GetMapping("/{id}")
    AlunoResponse buscar(@PathVariable UUID id) {
        return AlunoResponse.de(service.detalhar(id));
    }

    @PostMapping
    ResponseEntity<AlunoResponse> criar(@Valid @RequestBody AlunoRequest request) {
        if (request.codigoPublico() == null || request.codigoPublico().isBlank()) {
            throw new DadosInvalidosException("codigoPublico: nao deve estar em branco");
        }
        var detalhe = service.criar(request.codigoPublico(), request.matricula(), request.nome(),
            request.ativo() == null || request.ativo(), request.turmaId(), request.dataInicio());
        return ResponseEntity.created(URI.create("/api/v1/alunos/" + detalhe.aluno().getId()))
            .body(AlunoResponse.de(detalhe));
    }

    @PutMapping("/{id}")
    AlunoResponse atualizar(@PathVariable UUID id, @Valid @RequestBody AlunoRequest request) {
        if (request.ativo() == null) {
            throw new DadosInvalidosException("ativo: obrigatorio na atualizacao");
        }
        return AlunoResponse.de(service.atualizar(id, request.matricula(), request.nome(), request.ativo()));
    }

    @PostMapping("/{id}/transferencias")
    AlunoResponse transferir(@PathVariable UUID id, @Valid @RequestBody TransferenciaRequest request) {
        enturmacaoService.transferir(id, request.turmaId(), request.dataMudanca());
        return AlunoResponse.de(service.detalhar(id));
    }

    @PostMapping("/{id}/rosto")
    AlunoResponse cadastrarRosto(@PathVariable UUID id, @Valid @RequestBody CadastrarRostoRequest request) {
        return AlunoResponse.de(service.cadastrarRosto(id, request.fotoBase64()));
    }
}
