package br.com.frankcardoso.merenda.refeicao.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.fila.domain.AuditoriaConsumo;
import br.com.frankcardoso.merenda.fila.domain.MetodoIdentificacao;
import br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.refeicao.api.TipoRefeicaoRequest;
import br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:gradeservico;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class GradeRefeicoesServiceTest {

    @Autowired
    private GradeRefeicoesService service;

    @Autowired
    private AuditoriaConsumoRepository auditorias;

    @Test
    void editarNomeNaoReescreveHistorico() {
        var id = RefeicaoPadrao.LANCHE_DA_TARDE.id();
        var auditoriaId = UUID.randomUUID();
        auditorias.save(new AuditoriaConsumo(auditoriaId, null, null, LocalDate.of(2026, 9, 1),
            id, "Lanche da tarde", Instant.parse("2026-09-01T15:30:00Z"), MetodoIdentificacao.QR_CODE,
            ResultadoConsumo.AUTORIZADO, null, UUID.randomUUID(), "chave-historico", null, null));

        service.atualizar(id, new TipoRefeicaoRequest("Merenda da tarde", LocalTime.of(15, 0), LocalTime.of(16, 0),
            Set.of(Turno.TARDE)));

        assertThat(service.buscar(id).getNome()).isEqualTo("Merenda da tarde");
        var gravada = auditorias.findById(auditoriaId).orElseThrow();
        assertThat(gravada.getTipoRefeicaoId()).isEqualTo(id);
        assertThat(gravada.getRefeicaoNome()).isEqualTo("Lanche da tarde");
    }
}
