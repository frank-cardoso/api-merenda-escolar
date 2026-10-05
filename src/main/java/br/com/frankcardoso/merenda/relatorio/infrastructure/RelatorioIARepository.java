package br.com.frankcardoso.merenda.relatorio.infrastructure;

import br.com.frankcardoso.merenda.relatorio.domain.RelatorioIA;
import java.util.UUID;
import java.time.LocalDate;
import java.util.List;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RelatorioIARepository extends JpaRepository<RelatorioIA, UUID> {
    List<RelatorioIA> findTop20ByDataReferenciaAndTurnoOrderByCriadoEmDesc(LocalDate dataReferencia, Turno turno);
}
