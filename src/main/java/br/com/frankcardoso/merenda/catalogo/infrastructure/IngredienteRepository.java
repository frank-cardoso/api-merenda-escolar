package br.com.frankcardoso.merenda.catalogo.infrastructure;

import br.com.frankcardoso.merenda.catalogo.domain.Ingrediente;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredienteRepository extends JpaRepository<Ingrediente, UUID> {
}
