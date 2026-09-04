package br.com.frankcardoso.merenda.shared.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Catalogo de itens compartilhado com o historico sintetico, publicado por
 * scripts/gerar_historico_fake.py em dados/itens-catalogo.tsv.
 *
 * Existe para que o cardapio de demonstracao use o MESMO vocabulario do historico. Sem isso os
 * nomes divergem ("arroz" no cardapio, "Arroz branco" no historico), o ranking historico nunca
 * intersecta o cardapio do dia e a analise fica impedida de relacionar prato com execucao —
 * chegando a afirmar relacao onde nao ha dado que sustente.
 */
@Component
public class CatalogoItensDemonstracao {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogoItensDemonstracao.class);
    private static final String ARQUIVO = "classpath:dados/itens-catalogo.tsv";
    private static final String GRUPO_BAIXA_EXECUCAO = "BAIXA_EXECUCAO";

    /** Um em cada tres dias recebe itens de baixa execucao, para o padrao ficar observavel. */
    private static final int PROPORCAO_DIAS_COM_ITEM_RUIM = 3;

    private final List<Item> alta = new ArrayList<>();
    private final List<Item> baixa = new ArrayList<>();

    public CatalogoItensDemonstracao(ResourceLoader resourceLoader) {
        carregar(resourceLoader);
    }

    public boolean estaVazio() {
        return alta.isEmpty() && baixa.isEmpty();
    }

    /**
     * Monta um cardapio de 3 a 5 itens. Parte dos dias leva um item de baixa execucao
     * historica, o que da a analise material real para correlacionar.
     */
    public Cardapio sortearCardapio(Random rng) {
        if (estaVazio()) return Cardapio.PADRAO;

        List<Item> escolhidos = new ArrayList<>();
        // Dois itens ruins em vez de um: com apenas um em um cardapio de quatro, a queda de
        // consumo fica pequena demais para se distinguir da variacao diaria.
        boolean diaComItemRuim = !baixa.isEmpty() && rng.nextInt(PROPORCAO_DIAS_COM_ITEM_RUIM) == 0;
        if (diaComItemRuim) {
            escolhidos.add(baixa.get(rng.nextInt(baixa.size())));
            var segundo = baixa.get(rng.nextInt(baixa.size()));
            if (!escolhidos.contains(segundo)) escolhidos.add(segundo);
        }

        int total = 3 + rng.nextInt(3);
        var candidatos = new ArrayList<>(alta.isEmpty() ? baixa : alta);
        java.util.Collections.shuffle(candidatos, rng);
        for (Item candidato : candidatos) {
            if (escolhidos.size() >= total) break;
            if (!escolhidos.contains(candidato)) escolhidos.add(candidato);
        }

        return new Cardapio(escolhidos, rng);
    }

    /**
     * Fator de adesao esperado para um cardapio, relativo a media do catalogo.
     *
     * Faz o consumo do dia depender do que foi servido: um cardapio com itens de baixa execucao
     * historica rende menos consumo. Sem isso a adesao seria aleatoria e a correlacao entre
     * prato e consumo — justamente o que a analise deve encontrar — nao existiria no dado.
     */
    public double fatorDeAdesao(List<String> nomes) {
        if (estaVazio() || nomes.isEmpty()) return 1.0;

        // A referencia e a media do grupo de alta execucao, nao a do catalogo inteiro: o
        // cardapio e composto majoritariamente por esses itens, entao um cardapio "normal"
        // precisa render fator 1,0 e manter a taxa base. Usar a media do catalogo inteiro
        // (puxada para baixo pelos acompanhamentos) inflaria todo cardapio acima do teto.
        double referencia = media(alta.isEmpty() ? todosOsItens() : alta);
        if (referencia <= 0) return 1.0;

        var doCardapio = todosOsItens().stream()
            .filter(item -> nomes.contains(item.nome()))
            .toList();
        if (doCardapio.isEmpty()) return 1.0;

        return media(doCardapio) / referencia;
    }

    private List<Item> todosOsItens() {
        var todos = new ArrayList<>(alta);
        todos.addAll(baixa);
        return todos;
    }

    private double media(List<Item> itens) {
        return itens.stream().mapToDouble(Item::taxaExecucao).average().orElse(0);
    }

    private void carregar(ResourceLoader resourceLoader) {
        var recurso = resourceLoader.getResource(ARQUIVO);
        if (!recurso.exists()) {
            LOG.info("Catalogo de itens ausente ({}). O cardapio de demonstracao usara o padrao. "
                + "Rode scripts/gerar_historico_fake.py para gerar.", ARQUIVO);
            return;
        }

        try (var leitor = new BufferedReader(
            new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8))) {
            leitor.readLine(); // cabecalho
            String linha;
            while ((linha = leitor.readLine()) != null) {
                if (linha.isBlank()) continue;
                String[] campos = linha.split("\t", -1);
                var item = new Item(campos[0], Double.parseDouble(campos[1]));
                if (GRUPO_BAIXA_EXECUCAO.equals(campos[2])) baixa.add(item);
                else alta.add(item);
            }
        } catch (IOException | NumberFormatException exception) {
            LOG.warn("Falha ao ler o catalogo de itens: {}", exception.getMessage());
        }
    }

    public record Item(String nome, double taxaExecucao) {
    }

    public record Cardapio(List<Item> itens, String nomeDaRefeicao, String comoJson) {

        static final Cardapio PADRAO = new Cardapio(
            List.of(new Item("Arroz branco", 0.44), new Item("Feijao carioca", 0.44)),
            "Arroz e feijao",
            "[{\"nome\":\"Arroz branco\"},{\"nome\":\"Feijao carioca\"}]");

        Cardapio(List<Item> itens, Random rng) {
            this(itens, montarNome(itens), montarJson(itens, rng));
        }

        private static String montarNome(List<Item> itens) {
            return itens.stream().map(Item::nome).collect(Collectors.joining(", "));
        }

        private static String montarJson(List<Item> itens, Random rng) {
            return itens.stream()
                .map(item -> "{\"nome\":\"%s\",\"quantidade\":\"%d kg\"}"
                    .formatted(item.nome(), 4 + rng.nextInt(12)))
                .collect(Collectors.joining(",", "[", "]"));
        }
    }
}
