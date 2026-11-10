package br.com.frankcardoso.merenda.refeicao.infrastructure;

import br.com.frankcardoso.merenda.refeicao.domain.TipoRefeicao;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoRefeicaoRepository extends JpaRepository<TipoRefeicao, UUID> {

    List<TipoRefeicao> findAllByOrderByOrdemAsc();

    Optional<TipoRefeicao> findByNomeIgnoreCase(String nome);
}
