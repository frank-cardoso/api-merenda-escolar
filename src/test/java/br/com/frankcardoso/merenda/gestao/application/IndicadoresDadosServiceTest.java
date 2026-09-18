package br.com.frankcardoso.merenda.gestao.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.fila.domain.*;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.historico.domain.HistoricoConsumo;
import br.com.frankcardoso.merenda.historico.infrastructure.HistoricoConsumoRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class IndicadoresDadosServiceTest {
    @Autowired IndicadoresDadosService dados;
    @Autowired AlunoRepository alunos;
    @Autowired CardapioRepository cardapios;
    @Autowired AuditoriaConsumoRepository auditorias;
    @Autowired HistoricoConsumoRepository historicos;

    @Test
    void agregaPorTurmaSemContarBloqueiosOuRepeticoesComoNovosAlunos() {
        var data = LocalDate.of(2030, 1, 15);
        var aluno = alunos.save(new Aluno(UUID.randomUUID(), "TEST-IND", "TEST-IND", "Teste",
            "Turma Teste", true, Instant.now()));
        var cardapio = cardapios.save(new Cardapio(UUID.randomUUID(), data, Turno.MANHA,
            "Teste", "", "[]", 100, true));
        for (var resultado : new ResultadoConsumo[]{ResultadoConsumo.AUTORIZADO,
                ResultadoConsumo.AUTORIZADO, ResultadoConsumo.BLOQUEADO}) {
            auditorias.save(new AuditoriaConsumo(UUID.randomUUID(), aluno, cardapio, data,
                Turno.MANHA, Instant.now(), MetodoIdentificacao.QR_CODE, resultado, null,
                UUID.randomUUID(), null));
        }
        // Dia anterior não entra no atendimento do dia.
        auditorias.save(new AuditoriaConsumo(UUID.randomUUID(), aluno, cardapio, data.minusDays(1),
            Turno.MANHA, Instant.now(), MetodoIdentificacao.QR_CODE, ResultadoConsumo.AUTORIZADO,
            null, UUID.randomUUID(), null));
        historicos.save(new HistoricoConsumo(UUID.randomUUID(), data.minusDays(1), Turno.MANHA,
            1, "Almoço", "Arroz", 100, null, "TESTE"));
        historicos.save(new HistoricoConsumo(UUID.randomUUID(), data.minusDays(2), Turno.MANHA,
            1, "Almoço", "Arroz", 100, 0, "TESTE"));
        historicos.save(new HistoricoConsumo(UUID.randomUUID(), data.minusDays(60), Turno.MANHA,
            1, "Almoço", "Fora da janela", 100, 90, "TESTE"));

        var input = dados.preparar(data, Turno.MANHA);

        assertThat(input.consumosRegistrados()).isEqualTo(2);
        assertThat(input.alunosUnicos()).isEqualTo(1);
        assertThat(input.turmas()).singleElement().satisfies(turma -> {
            assertThat(turma.turma()).isEqualTo("Turma Teste");
            assertThat(turma.alunosUnicos()).isEqualTo(1);
            assertThat(turma.consumosRegistrados()).isEqualTo(2);
        });
        assertThat(input.itens()).singleElement().satisfies(item -> {
            assertThat(item.item()).isEqualTo("Arroz");
            assertThat(item.planejamentos()).isEqualTo(2);
            assertThat(item.execucoesRegistradas()).isEqualTo(1);
            assertThat(item.escolas()).isEqualTo(1);
        });
    }
}
