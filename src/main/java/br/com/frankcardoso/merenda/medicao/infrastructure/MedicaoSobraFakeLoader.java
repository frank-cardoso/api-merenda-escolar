package br.com.frankcardoso.merenda.medicao.infrastructure;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.medicao.domain.MedicaoSobra;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrega a medicao sintetica gerada por scripts/gerar_historico_fake.py. O catalogo (receitas e
 * ingredientes) vem antes, pelo CatalogoLoader.
 *
 * Mesma regra do historico: so roda com a tabela vazia, entao reiniciar nao duplica e apagar a
 * tabela recarrega.
 */
@Component
@Order(3)
public class MedicaoSobraFakeLoader {

    private static final Logger LOG = LoggerFactory.getLogger(MedicaoSobraFakeLoader.class);
    private static final String MEDICOES = "classpath:dados/medicao-sobra-fake.tsv";
    private static final int LOTE = 500;

    private final MedicaoSobraRepository medicoes;
    private final ResourceLoader resourceLoader;
    private final Clock clock;

    public MedicaoSobraFakeLoader(MedicaoSobraRepository medicoes, ResourceLoader resourceLoader,
        Clock clock) {
        this.medicoes = medicoes;
        this.resourceLoader = resourceLoader;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void carregar() {
        if (medicoes.count() == 0) {
            var registradoEm = Instant.now(clock);
            carregarArquivo(MEDICOES, "medição de sobra",
                linha -> converterMedicao(linha, registradoEm), medicoes::saveAll);
        }
    }

    private <T> void carregarArquivo(String caminho, String descricao,
        Function<String, T> conversor, java.util.function.Consumer<List<T>> gravar) {

        Resource recurso = resourceLoader.getResource(caminho);
        if (!recurso.exists()) {
            LOG.info("Arquivo de {} ausente ({}). Rode scripts/gerar_historico_fake.py.",
                descricao, caminho);
            return;
        }

        try (var leitor = new BufferedReader(
            new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8))) {

            leitor.readLine(); // cabecalho
            List<T> lote = new ArrayList<>(LOTE);
            long total = 0;

            String linha;
            while ((linha = leitor.readLine()) != null) {
                if (linha.isBlank()) continue;
                lote.add(conversor.apply(linha));
                if (lote.size() >= LOTE) {
                    gravar.accept(lote);
                    total += lote.size();
                    lote.clear();
                }
            }

            if (!lote.isEmpty()) {
                gravar.accept(lote);
                total += lote.size();
            }

            LOG.info("Carregado: {} — {} registros", descricao, total);
        } catch (IOException | RuntimeException excecao) {
            LOG.warn("Falha ao carregar {}: {}", descricao, excecao.getMessage());
        }
    }

    private MedicaoSobra converterMedicao(String linha, Instant registradoEm) {
        String[] campos = linha.split("\\t", -1);
        return new MedicaoSobra(
            UUID.fromString(campos[0]),
            LocalDate.parse(campos[1]),
            Turno.valueOf(campos[2]),
            Integer.parseInt(campos[3]),
            UUID.fromString(campos[4]),
            Integer.parseInt(campos[6]),
            Integer.parseInt(campos[7]),
            Integer.parseInt(campos[8]),
            Integer.parseInt(campos[9]),
            campos[10],
            registradoEm
        );
    }
}
