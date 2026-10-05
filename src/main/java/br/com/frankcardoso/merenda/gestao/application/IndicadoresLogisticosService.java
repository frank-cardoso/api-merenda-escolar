package br.com.frankcardoso.merenda.gestao.application;

import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticos;
import br.com.frankcardoso.merenda.analytics.application.port.IndicadoresLogisticosPort;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IndicadoresLogisticosService {
    private static final Logger LOG = LoggerFactory.getLogger(IndicadoresLogisticosService.class);
    private final IndicadoresDadosService dados;
    private final IndicadoresLogisticosPort port;

    public IndicadoresLogisticosService(IndicadoresDadosService dados, IndicadoresLogisticosPort port) {
        this.dados = dados;
        this.port = port;
    }

    public IndicadoresLogisticos calcular(LocalDate data, Turno turno) {
        return calcular(data, turno, List.of());
    }

    public IndicadoresLogisticos calcular(LocalDate data, Turno turno, List<UUID> receitasSelecionadas) {
        return calcular(data, turno, receitasSelecionadas, List.of());
    }

    public IndicadoresLogisticos calcular(LocalDate data, Turno turno,
        List<UUID> receitasSelecionadas, List<LocalDate> datasSelecionadas) {
        var input = receitasSelecionadas.isEmpty() && datasSelecionadas.isEmpty()
            ? dados.preparar(data, turno)
            : dados.preparar(data, turno, receitasSelecionadas, datasSelecionadas);
        try {
            return port.calcular(input);
        } catch (RuntimeException exception) {
            LOG.warn("Indicadores indisponíveis para {} / {}: {}", data, turno,
                exception.getClass().getSimpleName());
            return IndicadoresLogisticos.indisponivel(data, turno);
        }
    }
}
