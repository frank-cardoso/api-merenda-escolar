package br.com.frankcardoso.merenda.relatorio.application;

import static org.assertj.core.api.Assertions.assertThat;
import static br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao.LANCHE_DA_MANHA;
import static br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao.LANCHE_DA_NOITE;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.relatorio.domain.RelatorioIA;
import br.com.frankcardoso.merenda.relatorio.infrastructure.RelatorioIARepository;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RelatorioIndicadoresTest {
    @Autowired RelatorioIARepository repository;
    @Autowired RelatorioIAService service;
    @Autowired EntityManager em;

    @Test
    void consultaRetornaFotografiaPersistidaMesmoQuandoServicoPythonNaoEstaDisponivel() {
        var relatorio = RelatorioIA.pendente(LocalDate.of(2030, 1, 15), LANCHE_DA_MANHA.id(), Instant.now());
        relatorio.registrarIndicadores("""
            {"schemaVersion":"2","calculoVersao":"indicadores-v1","status":"DISPONIVEL",
             "dataReferencia":"2030-01-15","inicioHistorico":"2029-12-17","turno":"MANHA",
             "execucaoPlanejamento":{"refeicoesPlanejadas":100,"consumosRegistrados":80,
              "percentual":80,"metaPercentual":80,"diferencaMetaPp":0,"statusMeta":"ATINGIDA"},
             "atendimentos":{"alunosUnicos":70,"repeticoes":10},
             "topComidas":[],"porTurma":[],"ingredientes":null,"rotacaoCardapio":null,"avisos":[]}
            """);
        repository.saveAndFlush(relatorio);
        em.clear();
        var response = service.buscar(relatorio.getId());
        assertThat(response.indicadores().execucaoPlanejamento().consumosRegistrados()).isEqualTo(80);
        assertThat(response.indicadores().atendimentos().repeticoes()).isEqualTo(10);
    }

    @Test
    void relatorioAntigoComTurnoContinuaLegivelEListado() {
        var id = java.util.UUID.randomUUID();
        em.createNativeQuery("""
            insert into relatorio_ia (id, data_referencia, turno, tipo_refeicao_id, status, prompt_versao,
                indicadores_json, tentativas, criado_em, versao)
            values (:id, :data, 'MANHA', :refeicao, 'CONCLUIDO', 'v2-indicadores', :json, 1, :criado, 0)
            """)
            .setParameter("id", id)
            .setParameter("data", LocalDate.of(2030, 2, 11))
            .setParameter("refeicao", LANCHE_DA_MANHA.id())
            .setParameter("json", """
                {"schemaVersion":"4","calculoVersao":"indicadores-v1","status":"DISPONIVEL",
                 "dataReferencia":"2030-02-11","inicioHistorico":"2030-01-13","turno":"MANHA",
                 "execucaoPlanejamento":{"refeicoesPlanejadas":100,"consumosRegistrados":80,
                  "percentual":80,"metaPercentual":80,"diferencaMetaPp":0,"statusMeta":"ATINGIDA"},
                 "atendimentos":{"alunosUnicos":70,"repeticoes":10},
                 "topComidas":[],"porTurma":[],"ingredientes":null,"rotacaoCardapio":null,"avisos":[]}
                """)
            .setParameter("criado", java.sql.Timestamp.from(Instant.now()))
            .executeUpdate();
        em.clear();

        var response = service.buscar(id);

        assertThat(response.turno()).isEqualTo(Turno.MANHA);
        assertThat(response.refeicao()).isEqualTo("Lanche da manhã");
        assertThat(response.indicadores().turno()).isEqualTo(Turno.MANHA);
        assertThat(response.indicadores().refeicao()).isNull();
        assertThat(service.listar(LocalDate.of(2030, 2, 11), LANCHE_DA_MANHA.id()))
            .extracting(r -> r.id()).contains(id);
    }

    @Test
    void relatorioSemFotografiaContinuaConsultavel() {
        var relatorio = repository.saveAndFlush(RelatorioIA.pendente(
            LocalDate.of(2030, 1, 15), LANCHE_DA_NOITE.id(), Instant.now()));
        assertThat(service.buscar(relatorio.getId()).indicadores()).isNull();
    }
}
