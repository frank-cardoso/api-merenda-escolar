package br.com.frankcardoso.merenda.catalogo.infrastructure;

import br.com.frankcardoso.merenda.catalogo.domain.Ingrediente;
import br.com.frankcardoso.merenda.catalogo.domain.Receita;
import br.com.frankcardoso.merenda.catalogo.domain.ReceitaIngrediente;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * Carrega o catalogo de receitas e ingredientes antes de qualquer outro loader.
 *
 * A ordem importa: historico e medicao referenciam `receita.id` por FK, entao o catalogo tem de
 * existir primeiro. Dai o {@code @Order(1)} — sem ele a ordem entre listeners do mesmo evento nao
 * e garantida.
 */
@Component
@Order(1)
public class CatalogoLoader {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogoLoader.class);
    private static final String CATALOGO = "classpath:dados/itens-catalogo.tsv";
    private static final String COMPOSICAO = "classpath:dados/receitas-ingredientes.tsv";

    private final ReceitaRepository receitas;
    private final IngredienteRepository ingredientes;
    private final ReceitaIngredienteRepository composicao;
    private final ResourceLoader resourceLoader;

    public CatalogoLoader(ReceitaRepository receitas, IngredienteRepository ingredientes,
        ReceitaIngredienteRepository composicao, ResourceLoader resourceLoader) {
        this.receitas = receitas;
        this.ingredientes = ingredientes;
        this.composicao = composicao;
        this.resourceLoader = resourceLoader;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void carregar() {
        if (receitas.count() == 0) carregarReceitas();
        if (ingredientes.count() == 0 || composicao.count() == 0) carregarComposicao();
    }

    private void carregarReceitas() {
        // receita_id, item, taxa_execucao, grupo
        var linhas = ler(CATALOGO, "catálogo de receitas");
        var novas = linhas.stream()
            .filter(campos -> campos.length >= 2)
            .map(campos -> new Receita(UUID.fromString(campos[0]), campos[1], null))
            .toList();
        if (!novas.isEmpty()) {
            receitas.saveAll(novas);
            LOG.info("Catálogo de receitas carregado: {} receitas", novas.size());
        }
    }

    private void carregarComposicao() {
        // receita_id, item, ingrediente_id, ingrediente
        var linhas = ler(COMPOSICAO, "composição das receitas");
        Map<UUID, Ingrediente> porId = new LinkedHashMap<>();
        List<ReceitaIngrediente> vinculos = new ArrayList<>();

        for (var campos : linhas) {
            if (campos.length < 4) continue;
            var receitaId = UUID.fromString(campos[0]);
            var ingredienteId = UUID.fromString(campos[2]);
            porId.computeIfAbsent(ingredienteId, id -> new Ingrediente(id, campos[3], null));
            vinculos.add(new ReceitaIngrediente(UUID.randomUUID(), receitaId, ingredienteId));
        }

        if (porId.isEmpty()) return;
        ingredientes.saveAll(porId.values());
        composicao.saveAll(vinculos);
        LOG.info("Composição carregada: {} ingredientes, {} vínculos", porId.size(), vinculos.size());
    }

    private List<String[]> ler(String caminho, String descricao) {
        Resource recurso = resourceLoader.getResource(caminho);
        if (!recurso.exists()) {
            LOG.info("Arquivo de {} ausente ({}). Rode scripts/gerar_historico_fake.py.",
                descricao, caminho);
            return List.of();
        }

        try (var leitor = new BufferedReader(
            new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8))) {
            leitor.readLine(); // cabecalho
            List<String[]> linhas = new ArrayList<>();
            String linha;
            while ((linha = leitor.readLine()) != null) {
                if (!linha.isBlank()) linhas.add(linha.split("\\t", -1));
            }
            return linhas;
        } catch (IOException excecao) {
            LOG.warn("Falha ao carregar {}: {}", descricao, excecao.getMessage());
            return List.of();
        }
    }
}
