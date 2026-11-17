package br.com.frankcardoso.merenda.fila.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.fila.api.ValidarConsumoRequest;
import br.com.frankcardoso.merenda.fila.domain.AuditoriaConsumo;
import br.com.frankcardoso.merenda.fila.domain.MetodoIdentificacao;
import br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao;
import br.com.frankcardoso.merenda.shared.config.ApplicationConfig;
import br.com.frankcardoso.merenda.turma.domain.Enturmacao;
import br.com.frankcardoso.merenda.turma.domain.Turma;
import br.com.frankcardoso.merenda.turma.infrastructure.EnturmacaoRepository;
import br.com.frankcardoso.merenda.turma.infrastructure.TurmaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
    "merenda.demo.horario=12:00",
    "spring.datasource.url=jdbc:h2:mem:filarefeicao;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class FilaPorRefeicaoIntegracaoTest {

    @Autowired
    private ValidacaoConsumoService fila;

    @Autowired
    private AlunoRepository alunos;

    @Autowired
    private TurmaRepository turmas;

    @Autowired
    private EnturmacaoRepository enturmacoes;

    @Autowired
    private CardapioRepository cardapios;

    @Autowired
    private AuditoriaConsumoRepository auditorias;

    @Autowired
    private Clock clock;

    @Test
    void integralAutorizadoEParcialBarradoNoAlmoco() {
        var hoje = LocalDate.ofInstant(clock.instant(), ApplicationConfig.ZONA_OPERACIONAL);
        enturmar("ALU-T01", "Aluno Integral", turma("T-INT", Turno.INTEGRAL), hoje);
        enturmar("ALU-T02", "Aluno Manhã", turma("T-MAN", Turno.MANHA), hoje);
        cardapioDoAlmoco(hoje);

        var integral = fila.validar(qrCode("ALU-T01"));
        var repetido = fila.validar(qrCode("ALU-T01"));
        var parcial = fila.validar(qrCode("ALU-T02"));

        assertThat(integral.resultado()).isEqualTo(ResultadoConsumo.AUTORIZADO);
        assertThat(integral.refeicao()).isEqualTo("Almoço");
        assertThat(integral.cardapio()).isEqualTo("Arroz e feijão");
        assertThat(repetido.resultado()).isEqualTo(ResultadoConsumo.BLOQUEADO);
        assertThat(repetido.motivo()).isEqualTo("CONSUMO_RECENTE_BLOQUEADO");
        assertThat(parcial.resultado()).isEqualTo(ResultadoConsumo.BLOQUEADO);
        assertThat(parcial.motivo()).isEqualTo("REFEICAO_NAO_PREVISTA_PARA_TURMA");
        assertThat(parcial.alunoNome()).isEqualTo("Aluno Manhã");
        assertThat(parcial.refeicao()).isEqualTo("Almoço");
        assertThat(parcial.turnoDaTurma()).isEqualTo("MANHA");
        assertThat(parcial.turnosDaRefeicao()).containsExactly("INTEGRAL");
        assertThat(integral.turnoDaTurma()).isNull();
        assertThat(integral.turnosDaRefeicao()).isNull();
        assertThat(repetido.turnoDaTurma()).isNull();
        assertThat(repetido.turnosDaRefeicao()).isNull();
    }

    @Test
    void consumoAutorizadoDepoisDoRelogioNaoBloqueiaAPassagem() {
        // Base local guarda passagens de quando a API rodou com relógio adiante (hora real ou
        // merenda.demo.horario mais tarde); para o relógio atual elas estão no futuro.
        var hoje = LocalDate.ofInstant(clock.instant(), ApplicationConfig.ZONA_OPERACIONAL);
        var aluno = enturmar("ALU-T03", "Aluno Futuro", turma("T-FUT", Turno.INTEGRAL), hoje);
        var cardapio = cardapioDoAlmoco(hoje);
        auditorias.save(new AuditoriaConsumo(UUID.randomUUID(), aluno, cardapio, hoje, RefeicaoPadrao.ALMOCO.id(),
            "Almoço", clock.instant().plus(Duration.ofMinutes(5)), MetodoIdentificacao.QR_CODE,
            ResultadoConsumo.AUTORIZADO, null, UUID.randomUUID(), null, null, null));

        var resposta = fila.validar(qrCode("ALU-T03"));

        assertThat(resposta.resultado()).isEqualTo(ResultadoConsumo.AUTORIZADO);
    }

    private Cardapio cardapioDoAlmoco(LocalDate dia) {
        return cardapios.findByDataAndTipoRefeicaoIdAndAtivoTrue(dia, RefeicaoPadrao.ALMOCO.id())
            .orElseGet(() -> cardapios.save(new Cardapio(UUID.randomUUID(), dia, RefeicaoPadrao.ALMOCO.id(),
                "Arroz e feijão", null, "[{\"nome\":\"Arroz\"}]", 100, true)));
    }

    private Turma turma(String codigo, Turno turno) {
        return turmas.save(new Turma(UUID.randomUUID(), codigo, "Turma " + codigo, turno, 30, null,
            clock.instant()));
    }

    private Aluno enturmar(String codigo, String nome, Turma turma, LocalDate desde) {
        var aluno = alunos.save(new Aluno(UUID.randomUUID(), codigo, codigo, nome, true, null, clock.instant()));
        enturmacoes.save(new Enturmacao(UUID.randomUUID(), aluno, turma, desde, null, null));
        return aluno;
    }

    private static ValidarConsumoRequest qrCode(String codigo) {
        return new ValidarConsumoRequest(codigo, MetodoIdentificacao.QR_CODE, null);
    }
}
