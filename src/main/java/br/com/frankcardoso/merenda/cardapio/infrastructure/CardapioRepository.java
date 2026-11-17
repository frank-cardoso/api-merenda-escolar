package br.com.frankcardoso.merenda.cardapio.infrastructure;

import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardapioRepository extends JpaRepository<Cardapio, UUID> {
    Optional<Cardapio> findByDataAndTipoRefeicaoIdAndAtivoTrue(LocalDate data, UUID tipoRefeicaoId);
    Optional<Cardapio> findByIdAndAtivoTrue(UUID id);
    List<Cardapio> findAllByAtivoTrueOrderByDataDesc();
    List<Cardapio> findAllByDataBetweenAndTipoRefeicaoIdAndAtivoTrueOrderByDataAsc(
        LocalDate inicio, LocalDate fim, UUID tipoRefeicaoId);
    boolean existsByDataAndTipoRefeicaoIdAndAtivoTrue(LocalDate data, UUID tipoRefeicaoId);
}
