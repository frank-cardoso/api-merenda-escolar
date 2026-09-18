package br.com.frankcardoso.merenda.relatorio.application;

import static org.assertj.core.api.Assertions.assertThat;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.relatorio.domain.RelatorioIA;
import br.com.frankcardoso.merenda.relatorio.infrastructure.RelatorioIARepository;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class RelatorioIndicadoresTest {
    @Autowired RelatorioIARepository repository;
    @Autowired RelatorioIAService service;
    @Autowired EntityManager em;

    @Test
    void consultaRetornaFotografiaPersistidaMesmoQuandoServicoPythonNaoEstaDisponivel() {
        var relatorio = RelatorioIA.pendente(LocalDate.of(2030, 1, 15), Turno.MANHA, Instant.now());
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
    void relatorioSemFotografiaContinuaConsultavel() {
        var relatorio = repository.saveAndFlush(RelatorioIA.pendente(
            LocalDate.of(2030, 1, 15), Turno.NOITE, Instant.now()));
        assertThat(service.buscar(relatorio.getId()).indicadores()).isNull();
    }
}
