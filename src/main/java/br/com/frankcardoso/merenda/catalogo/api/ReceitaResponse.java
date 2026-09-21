package br.com.frankcardoso.merenda.catalogo.api;

import br.com.frankcardoso.merenda.catalogo.domain.Receita;
import java.util.UUID;

public record ReceitaResponse(UUID id, String nome, String codigoExterno) {
    public static ReceitaResponse de(Receita receita) {
        return new ReceitaResponse(receita.getId(), receita.getNome(), receita.getCodigoExterno());
    }
}
