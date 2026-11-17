package br.com.frankcardoso.merenda.refeicao.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class MigracaoRefeicoesTest {

    private static final String URL = "jdbc:h2:mem:migracao-refeicoes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    private static final String DATA = "2026-03-02";

    @Test
    void dadosAntigosRecebemARefeicaoMapeada() throws SQLException {
        Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration")
            .target("14").load().migrate();

        var receita = UUID.randomUUID();
        var cardapioIntegral = UUID.randomUUID();
        var cardapioManha = UUID.randomUUID();
        var auditoria = UUID.randomUUID();
        var medicao = UUID.randomUUID();
        var relatorioNoite = UUID.randomUUID();
        var relatorioSemTurno = UUID.randomUUID();
        Map<UUID, String> historico = new LinkedHashMap<>();
        for (String nome : new String[] {"Cafe de manha - Turnos Integrais",
                "Cafe da manha EMEIS ou menores de 1 ano", "Almoco - EMEIS",
                "Lanche da manha - Jardim EMEIS", "Lanche da tarde, menores de um ano - EMEIS",
                "Lanches manha ou tarde - EMEFS parcial", "Lanche do turno estendido - EMEIS",
                "Frutas - Lanches", "Lanches manha e tarde - Jardim EMEIS"}) {
            historico.put(UUID.randomUUID(), nome);
        }

        try (Connection c = DriverManager.getConnection(URL, "sa", "")) {
            executar(c, "INSERT INTO receita (id, nome, ativo) VALUES (?, 'Arroz de teste', TRUE)", receita);
            for (var linha : new Object[][] {{cardapioIntegral, "INTEGRAL"}, {cardapioManha, "MANHA"}}) {
                executar(c, "INSERT INTO cardapio (id, data, turno, nome_refeicao, itens_json, "
                    + "quantidade_planejada, ativo) VALUES (?, DATE '" + DATA + "', ?, 'Refeição', '[]', 10, FALSE)",
                    linha);
            }
            executar(c, "INSERT INTO auditoria_consumo (id, data_operacional, turno, instante, "
                + "metodo_identificacao, resultado, correlation_id) VALUES (?, DATE '" + DATA
                + "', 'TARDE', TIMESTAMP WITH TIME ZONE '2026-03-02 15:30:00+00', 'QR_CODE', 'AUTORIZADO', ?)",
                auditoria, UUID.randomUUID());
            executar(c, "INSERT INTO medicao_sobra (id, data, turno, escola_id, receita_id, porcoes_preparadas, "
                + "porcoes_servidas, sobra_nao_distribuida, resto_no_prato, origem, registrado_em) "
                + "VALUES (?, DATE '" + DATA + "', 'MANHA', 1, ?, 10, 8, 2, 1, 'MANUAL', TIMESTAMP '2026-03-02 12:00:00')",
                medicao, receita);
            for (var linha : new Object[][] {{relatorioNoite, "NOITE"}, {relatorioSemTurno, null}}) {
                executar(c, "INSERT INTO relatorio_ia (id, data_referencia, turno, status, prompt_versao, "
                    + "tentativas, criado_em) VALUES (?, DATE '" + DATA + "', ?, 'PENDENTE', 'v2', 0, "
                    + "TIMESTAMP WITH TIME ZONE '2026-03-02 12:00:00+00')", linha);
            }
            for (var e : historico.entrySet()) {
                executar(c, "INSERT INTO historico_consumo (id, data, turno, escola_id, refeicao, receita_id, "
                    + "quantidade_planejada, origem) VALUES (?, DATE '" + DATA + "', 'INTEGRAL', 1, ?, ?, 10, 'SINTETICO')",
                    e.getKey(), e.getValue(), receita);
            }
        }

        Flyway.configure().dataSource(URL, "sa", "").locations("classpath:db/migration").load().migrate();

        try (Connection c = DriverManager.getConnection(URL, "sa", "")) {
            assertThat(refeicao(c, "cardapio", cardapioIntegral)).isEqualTo(RefeicaoPadrao.ALMOCO.id());
            assertThat(refeicao(c, "cardapio", cardapioManha)).isEqualTo(RefeicaoPadrao.LANCHE_DA_MANHA.id());
            assertThat(refeicao(c, "auditoria_consumo", auditoria)).isEqualTo(RefeicaoPadrao.LANCHE_DA_TARDE.id());
            assertThat(texto(c, "SELECT refeicao_nome FROM auditoria_consumo WHERE id = ?", auditoria))
                .isEqualTo("Lanche da tarde");
            assertThat(refeicao(c, "medicao_sobra", medicao)).isEqualTo(RefeicaoPadrao.LANCHE_DA_MANHA.id());
            assertThat(refeicao(c, "relatorio_ia", relatorioNoite)).isEqualTo(RefeicaoPadrao.LANCHE_DA_NOITE.id());
            assertThat(refeicao(c, "relatorio_ia", relatorioSemTurno)).isNull();
            for (var e : historico.entrySet()) {
                var esperado = RefeicaoPadrao.doNomeLegado(e.getValue()).map(RefeicaoPadrao::id).orElse(null);
                assertThat(refeicao(c, "historico_consumo", e.getKey())).as(e.getValue()).isEqualTo(esperado);
            }
        }
    }

    @Test
    void turnoSaiDasTabelasPorRefeicaoEFicaNoRelatorio() throws SQLException {
        var url = "jdbc:h2:mem:migracao-refeicoes-contract;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();

        try (Connection c = DriverManager.getConnection(url, "sa", "")) {
            for (String tabela : new String[] {"cardapio", "auditoria_consumo", "medicao_sobra", "historico_consumo"}) {
                assertThat(temColunaTurno(c, tabela)).as(tabela).isFalse();
            }
            assertThat(temColunaTurno(c, "relatorio_ia")).isTrue();
        }
    }

    private static boolean temColunaTurno(Connection c, String tabela) throws SQLException {
        try (ResultSet rs = c.getMetaData().getColumns(null, null, tabela.toUpperCase(), "TURNO")) {
            return rs.next();
        }
    }

    private static void executar(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (Object p : params) {
                ps.setObject(i++, p);
            }
            ps.executeUpdate();
        }
    }

    private static UUID refeicao(Connection c, String tabela, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT tipo_refeicao_id FROM " + tabela + " WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getObject(1, UUID.class);
            }
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
