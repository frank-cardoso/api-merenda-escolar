package br.com.frankcardoso.merenda.gestao.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.fila.domain.*;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.historico.domain.HistoricoConsumo;
import br.com.frankcardoso.merenda.catalogo.domain.Receita;
import br.com.frankcardoso.merenda.catalogo.infrastructure.ReceitaRepository;
import br.com.frankcardoso.merenda.historico.infrastructure.HistoricoConsumoRepository;
import br.com.frankcardoso.merenda.turma.application.EnturmacaoService;
import br.com.frankcardoso.merenda.turma.domain.Turma;
import br.com.frankcardoso.merenda.turma.infrastructure.EnturmacaoRepository;
import br.com.frankcardoso.merenda.turma.infrastructure.TurmaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IndicadoresDadosServiceTest {

    private static final UUID ID_ARROZ = UUID.randomUUID();
    private static final UUID ID_FORA = UUID.randomUUID();
    @Autowired IndicadoresDadosService dados;
    @Autowired AlunoRepository alunos;
    @Autowired CardapioRepository cardapios;
    @Autowired AuditoriaConsumoRepository auditorias;
    @Autowired HistoricoConsumoRepository historicos;
    @Autowired ReceitaRepository receitas;
    @Autowired TurmaRepository turmas;
    @Autowired EnturmacaoService enturmacao;
    @Autowired EnturmacaoRepository enturmacoes;

    @Test
    void agregaPorTurmaSemContarBloqueiosOuRepeticoesComoNovosAlunos() {
        var data = LocalDate.of(2030, 1, 15);
        // O historico referencia receita por FK: o catalogo precisa existir antes.
        receitas.save(new Receita(ID_ARROZ, "Arroz", null));
        receitas.save(new Receita(ID_FORA, "Fora da janela", null));
        var aluno = alunos.save(new Aluno(UUID.randomUUID(), "TEST-IND", "TEST-IND", "Teste",
            true, null, Instant.now()));
        var turmaTeste = turma("Turma Teste");
        var cardapio = cardapios.save(new Cardapio(UUID.randomUUID(), data, Turno.MANHA,
            "Teste", "", "[]", 100, true));
        for (var resultado : new ResultadoConsumo[]{ResultadoConsumo.AUTORIZADO,
                ResultadoConsumo.AUTORIZADO, ResultadoConsumo.BLOQUEADO}) {
            auditorias.save(new AuditoriaConsumo(UUID.randomUUID(), aluno, cardapio, data,
                Turno.MANHA, Instant.now(), MetodoIdentificacao.QR_CODE, resultado, null,
                UUID.randomUUID(), null, turmaTeste.getId(), turmaTeste.getCodigo()));
        }
        // Dia anterior não entra no atendimento do dia.
        auditorias.save(new AuditoriaConsumo(UUID.randomUUID(), aluno, cardapio, data.minusDays(1),
            Turno.MANHA, Instant.now(), MetodoIdentificacao.QR_CODE, ResultadoConsumo.AUTORIZADO,
            null, UUID.randomUUID(), null, turmaTeste.getId(), turmaTeste.getCodigo()));
        historicos.save(new HistoricoConsumo(UUID.randomUUID(), data.minusDays(1), Turno.MANHA,
            1, "Almoço", ID_ARROZ, 100, null, "TESTE"));
        historicos.save(new HistoricoConsumo(UUID.randomUUID(), data.minusDays(2), Turno.MANHA,
            1, "Almoço", ID_ARROZ, 100, 0, "TESTE"));
        historicos.save(new HistoricoConsumo(UUID.randomUUID(), data.minusDays(60), Turno.MANHA,
            1, "Almoço", ID_FORA, 100, 90, "TESTE"));

        var input = dados.preparar(data, Turno.MANHA);

        assertThat(input.consumosRegistrados()).isEqualTo(2);
        assertThat(input.alunosUnicos()).isEqualTo(1);
        assertThat(input.turmas()).singleElement().satisfies(turma -> {
            assertThat(turma.turmaId()).isEqualTo(turmaTeste.getId());
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

    @Test
    void agrupaPelaTurmaCarimbadaNaoPelaTurmaAtual() {
        var data = LocalDate.of(2030, 8, 12);
        var turma7A = turma("7A");
        var turma8A = turma("8A");
        var aluno = alunos.save(new Aluno(UUID.randomUUID(), "TEST-CAR", "TEST-CAR", "Carimbo",
            true, null, Instant.now()));
        var cardapio = cardapios.save(new Cardapio(UUID.randomUUID(), data, Turno.MANHA,
            "Teste", "", "[]", 100, true));
        // Estava na 7A ate 19/08 e foi transferido para a 8A em 20/08: a turma vigente do aluno
        // e a 8A, mas o consumo de 12/08 foi carimbado 7A e o carimbo manda.
        enturmacao.enturmar(aluno.getId(), turma7A.getId(), LocalDate.of(2030, 8, 1));
        enturmacao.transferir(aluno.getId(), turma8A.getId(), LocalDate.of(2030, 8, 20));
        assertThat(enturmacoes.vigenteEm(aluno.getId(), LocalDate.of(2030, 8, 25)))
            .get().extracting(e -> e.getTurma().getId()).isEqualTo(turma8A.getId());
        consumo(aluno, cardapio, data, turma7A.getId(), "7A");

        var input = dados.preparar(data, Turno.MANHA);

        assertThat(input.turmas()).singleElement().satisfies(t -> {
            assertThat(t.turmaId()).isEqualTo(turma7A.getId());
            assertThat(t.turma()).isEqualTo("7A");
            assertThat(t.consumosRegistrados()).isEqualTo(1);
        });
        assertThat(input.turmas()).noneMatch(t -> t.turmaId() != null && t.turmaId().equals(turma8A.getId()));
    }

    @Test
    void consumoSemTurmaEntraNoBaldeESomaNoTotal() {
        var data = LocalDate.of(2030, 8, 13);
        var turma = turma("9A");
        var cardapio = cardapios.save(new Cardapio(UUID.randomUUID(), data, Turno.MANHA,
            "Teste", "", "[]", 100, true));
        for (int i = 0; i < 3; i++) {
            consumo(aluno("COM" + i), cardapio, data, turma.getId(), "9A");
        }
        for (int i = 0; i < 2; i++) {
            consumo(aluno("SEM" + i), cardapio, data, null, AuditoriaConsumo.SEM_TURMA);
        }

        var input = dados.preparar(data, Turno.MANHA);

        assertThat(input.alunosUnicos()).isEqualTo(5);
        assertThat(input.turmas()).hasSize(2);
        assertThat(input.turmas()).filteredOn(t -> t.turmaId() == null).singleElement().satisfies(t -> {
            assertThat(t.turma()).isEqualTo(AuditoriaConsumo.SEM_TURMA);
            assertThat(t.alunosUnicos()).isEqualTo(2);
        });
        assertThat(input.turmas()).filteredOn(t -> t.turmaId() != null).singleElement()
            .satisfies(t -> assertThat(t.alunosUnicos()).isEqualTo(3));
    }

    @Test
    void linhaAnteriorAV10SemTurmaNemNomeEntraNoBaldeSemTurmaJuntoComAsCarimbadas() {
        var data = LocalDate.of(2030, 9, 17);
        var turma = turma("10A");
        var cardapio = cardapios.save(new Cardapio(UUID.randomUUID(), data, Turno.MANHA,
            "Teste", "", "[]", 100, true));
        consumo(aluno("NOVO0"), cardapio, data, turma.getId(), "10A");
        // Simula consumo gravado antes da V10: turma_id e turma_nome nulos.
        consumo(aluno("LEG0"), cardapio, data, null, null);
        consumo(aluno("LEG1"), cardapio, data, null, null);
        consumo(aluno("SEM0"), cardapio, data, null, AuditoriaConsumo.SEM_TURMA);

        var input = dados.preparar(data, Turno.MANHA);

        assertThat(input.turmas()).allSatisfy(t -> assertThat(t.turma()).isNotNull());
        assertThat(input.alunosUnicos()).isEqualTo(4);
        assertThat(input.turmas()).hasSize(2);
        assertThat(input.turmas()).filteredOn(t -> t.turmaId() == null).singleElement().satisfies(t -> {
            assertThat(t.turma()).isEqualTo(AuditoriaConsumo.SEM_TURMA);
            assertThat(t.alunosUnicos()).isEqualTo(3);
            assertThat(t.consumosRegistrados()).isEqualTo(3);
        });
    }

    private Turma turma(String codigo) {
        return turmas.save(new Turma(UUID.randomUUID(), codigo, "Descricao " + codigo, Turno.MANHA,
            30, null, Instant.now()));
    }

    private Aluno aluno(String codigo) {
        return alunos.save(new Aluno(UUID.randomUUID(), codigo, codigo, codigo, true, null, Instant.now()));
    }

    private void consumo(Aluno aluno, Cardapio cardapio, LocalDate data, UUID turmaId, String turmaNome) {
        auditorias.save(new AuditoriaConsumo(UUID.randomUUID(), aluno, cardapio, data, Turno.MANHA,
            Instant.now(), MetodoIdentificacao.QR_CODE, ResultadoConsumo.AUTORIZADO, null,
            UUID.randomUUID(), null, turmaId, turmaNome));
    }
}
