package br.com.frankcardoso.merenda.relatorio.infrastructure;

import br.com.frankcardoso.merenda.relatorio.domain.RelatorioIA;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RelatorioIARepository extends JpaRepository<RelatorioIA, UUID> {
}
