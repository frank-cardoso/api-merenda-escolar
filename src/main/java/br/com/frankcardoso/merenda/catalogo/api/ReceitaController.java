package br.com.frankcardoso.merenda.catalogo.api;

import br.com.frankcardoso.merenda.catalogo.infrastructure.ReceitaRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catalogo para o cadastro de cardapio escolher receita por id em vez de digitar nome. */
@RestController
@RequestMapping("/api/v1/receitas")
public class ReceitaController {

    private final ReceitaRepository receitas;

    public ReceitaController(ReceitaRepository receitas) {
        this.receitas = receitas;
    }

    @GetMapping
    List<ReceitaResponse> listar() {
        return receitas.findAllByAtivoTrueOrderByNome().stream().map(ReceitaResponse::de).toList();
    }
}
