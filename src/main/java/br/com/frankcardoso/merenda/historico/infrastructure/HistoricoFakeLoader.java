package br.com.frankcardoso.merenda.historico.infrastructure;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.historico.domain.HistoricoConsumo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrega o historico sintetico gerado por scripts/gerar_historico_fake.py.
 *
 * Roda uma vez, apenas se a tabela estiver vazia — assim reiniciar a aplicacao nao duplica
 * dados, e apagar a tabela e suficiente para recarregar depois de regerar o CSV.
 */
@Component
public class HistoricoFakeLoader {

    private static final Logger LOG = LoggerFactory.getLogger(HistoricoFakeLoader.class);
    private static final String ARQUIVO = "classpath:dados/historico-fake.tsv";
    private static final int LOTE = 500;

    private final HistoricoConsumoRepository repository;
    private final ResourceLoader resourceLoader;

    public HistoricoFakeLoader(HistoricoConsumoRepository repository, ResourceLoader resourceLoader) {
        this.repository = repository;
        this.resourceLoader = resourceLoader;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void carregar() {
        if (repository.count() > 0) return;

        Resource recurso = resourceLoader.getResource(ARQUIVO);
        if (!recurso.exists()) {
            LOG.info("Historico sintetico ausente ({}). Rode scripts/gerar_historico_fake.py "
                + "para habilitar a analise com historico.", ARQUIVO);
            return;
        }

        try (var leitor = new BufferedReader(
            new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8))) {

            leitor.readLine(); // cabecalho
            List<HistoricoConsumo> lote = new ArrayList<>(LOTE);
            long total = 0;

            String linha;
            while ((linha = leitor.readLine()) != null) {
                if (linha.isBlank()) continue;
                lote.add(converter(linha));
                if (lote.size() >= LOTE) {
                    repository.saveAll(lote);
                    total += lote.size();
                    lote.clear();
                }
            }

            if (!lote.isEmpty()) {
                repository.saveAll(lote);
                total += lote.size();
            }

            LOG.info("Historico sintetico carregado: {} registros", total);
        } catch (IOException exception) {
            LOG.warn("Falha ao carregar o historico sintetico: {}", exception.getMessage());
        }
    }

    private HistoricoConsumo converter(String linha) {
        String[] campos = linha.split("\\t", -1);
        return new HistoricoConsumo(
            UUID.fromString(campos[0]),
            LocalDate.parse(campos[1]),
            Turno.valueOf(campos[2]),
            Integer.parseInt(campos[3]),
            campos[4],
            campos[5],
            Integer.parseInt(campos[6]),
            campos[7].isBlank() ? null : Integer.parseInt(campos[7]),
            campos[8]
        );
    }
}
