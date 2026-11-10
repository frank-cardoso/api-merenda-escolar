package br.com.frankcardoso.merenda.catalogo.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class MigracaoAcentosTest {

    private static final Map<String, String> RECEITAS = mapa(
        "Pure de mandioca", "Purê de mandioca",
        "Molho de carne moida", "Molho de carne moída",
        "Pure de batata", "Purê de batata",
        "Carne moida tipo pastel", "Carne moída tipo pastel",
        "Brocolis", "Brócolis",
        "Requeijao caseiro", "Requeijão caseiro",
        "Couve flor", "Couve-flor",
        "Carne suina acebolada", "Carne suína acebolada",
        "Pure de moranga", "Purê de moranga",
        "Bolo de maca, sem acucar", "Bolo de maçã, sem açúcar",
        "Feijao carioca", "Feijão carioca",
        "Pao com manteiga", "Pão com manteiga",
        "Maca", "Maçã",
        "Macarrao ao sugo", "Macarrão ao sugo");

    private static final Map<String, String> INGREDIENTES = mapa(
        "brocolis", "brócolis",
        "couve flor", "couve-flor",
        "fuba", "fubá",
        "carne suina", "carne suína",
        "maca", "maçã",
        "feijao", "feijão",
        "acucar", "açúcar");

    @Test
    void baseExistenteFicaComOsNomesAcentuadosEOsMesmosIds() throws SQLException {
        var url = "jdbc:h2:mem:migracao-acentos;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
            .target("16").load().migrate();

        Map<String, UUID> receitas = new LinkedHashMap<>();
        Map<String, UUID> ingredientes = new LinkedHashMap<>();
        var cardapio = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            for (String nome : nomesComIntactos(RECEITAS, "Arroz branco", "Iscas de frango acebolada")) {
                receitas.put(nome, UUID.randomUUID());
                executar(c, "INSERT INTO receita (id, nome, ativo) VALUES (?, ?, TRUE)", receitas.get(nome), nome);
            }
            for (String nome : nomesComIntactos(INGREDIENTES, "cenoura", "batata")) {
                ingredientes.put(nome, UUID.randomUUID());
                executar(c, "INSERT INTO ingrediente (id, nome) VALUES (?, ?)", ingredientes.get(nome), nome);
            }
            var itens = List.of("Macarrao ao sugo", "Maca", "Bolo de maca, sem acucar", "Arroz branco");
            String json = itens.stream()
                .map(nome -> "{\"receitaId\":\"%s\",\"nome\":\"%s\",\"quantidade\":\"5 kg\"}"
                    .formatted(receitas.get(nome), nome))
                .collect(Collectors.joining(",", "[", "]"));
            executar(c, "INSERT INTO cardapio (id, data, tipo_refeicao_id, nome_refeicao, descricao, itens_json, "
                + "quantidade_planejada, ativo) VALUES (?, DATE '2026-03-02', ?, ?, 'Cardapio de demonstracao', ?, 10, TRUE)",
                cardapio, RefeicaoPadrao.ALMOCO.id(), String.join(", ", itens), json);
        }

        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();

        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            Map<UUID, String> receitasMigradas = nomesPorId(c, "receita");
            Map<UUID, String> ingredientesMigrados = nomesPorId(c, "ingrediente");
            receitas.forEach((antigo, id) -> assertThat(receitasMigradas.get(id)).as(antigo)
                .isEqualTo(RECEITAS.getOrDefault(antigo, antigo)));
            ingredientes.forEach((antigo, id) -> assertThat(ingredientesMigrados.get(id)).as(antigo)
                .isEqualTo(INGREDIENTES.getOrDefault(antigo, antigo)));
            assertThat(receitasMigradas.values()).doesNotContainAnyElementsOf(RECEITAS.keySet());
            assertThat(ingredientesMigrados.values()).doesNotContainAnyElementsOf(INGREDIENTES.keySet());

            assertThat(texto(c, "SELECT nome_refeicao FROM cardapio WHERE id = ?", cardapio))
                .isEqualTo("Macarrão ao sugo, Maçã, Bolo de maçã, sem açúcar, Arroz branco");
            assertThat(texto(c, "SELECT itens_json FROM cardapio WHERE id = ?", cardapio))
                .contains("\"receitaId\":\"%s\",\"nome\":\"Macarrão ao sugo\"".formatted(receitas.get("Macarrao ao sugo")))
                .contains("\"receitaId\":\"%s\",\"nome\":\"Maçã\"".formatted(receitas.get("Maca")))
                .contains("\"nome\":\"Bolo de maçã, sem açúcar\"")
                .contains("\"nome\":\"Arroz branco\"");
        }
    }

    @Test
    void dadosDeCargaJaVemComOsNomesAcentuados() throws IOException {
        var catalogo = coluna("dados/itens-catalogo.tsv", 1);
        assertThat(catalogo).containsAll(RECEITAS.values()).doesNotContainAnyElementsOf(RECEITAS.keySet());

        assertThat(coluna("dados/receitas-ingredientes.tsv", 1)).doesNotContainAnyElementsOf(RECEITAS.keySet());
        assertThat(coluna("dados/receitas-ingredientes.tsv", 3))
            .containsAll(INGREDIENTES.values()).doesNotContainAnyElementsOf(INGREDIENTES.keySet());
        assertThat(coluna("dados/historico-fake.tsv", 6)).doesNotContainAnyElementsOf(RECEITAS.keySet());
        assertThat(coluna("dados/medicao-sobra-fake.tsv", 5)).doesNotContainAnyElementsOf(RECEITAS.keySet());
    }

    private static Map<String, String> mapa(String... pares) {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) mapa.put(pares[i], pares[i + 1]);
        return mapa;
    }

    private static List<String> nomesComIntactos(Map<String, String> mapa, String... intactos) {
        var nomes = new ArrayList<>(mapa.keySet());
        nomes.addAll(List.of(intactos));
        return nomes;
    }

    private static List<String> coluna(String recurso, int indice) throws IOException {
        var entrada = MigracaoAcentosTest.class.getClassLoader().getResourceAsStream(recurso);
        assertThat(entrada).as(recurso).isNotNull();
        try (var leitor = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
            return leitor.lines().skip(1).filter(linha -> !linha.isBlank())
                .map(linha -> linha.split("\t", -1)[indice]).distinct().toList();
        }
    }

    private static Map<UUID, String> nomesPorId(Connection c, String tabela) throws SQLException {
        Map<UUID, String> nomes = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id, nome FROM " + tabela);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) nomes.put(rs.getObject(1, UUID.class), rs.getString(2));
        }
        return nomes;
    }

    private static void executar(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (Object p : params) ps.setObject(i++, p);
            ps.executeUpdate();
        }
    }

    private static String texto(Connection c, String sql, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }
}
