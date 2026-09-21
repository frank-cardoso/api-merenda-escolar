package br.com.frankcardoso.merenda.catalogo.infrastructure;

import br.com.frankcardoso.merenda.catalogo.domain.Receita;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceitaRepository extends JpaRepository<Receita, UUID> {
    Optional<Receita> findByNome(String nome);
    List<Receita> findAllByAtivoTrueOrderByNome();
}
