package br.com.frankcardoso.merenda.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.turma.infrastructure.EnturmacaoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RostosDemonstracaoLoaderTest {

    private static final Resource SEMENTE = new ClassPathResource("demo/rostos-demo-teste.json");

    @Autowired AlunoRepository alunos;
    @Autowired AuditoriaConsumoRepository auditorias;
    @Autowired EnturmacaoRepository enturmacoes;
    @Autowired ObjectMapper objectMapper;
    @Autowired EntityManager em;

    @BeforeEach
    void limpar() {
        auditorias.deleteAll();
        enturmacoes.deleteAll();
        alunos.deleteAll();
        em.flush();
    }

    private RostosDemonstracaoLoader loader(Resource semente) {
        return new RostosDemonstracaoLoader(alunos, objectMapper, Clock.systemUTC(), semente);
    }

    @Test
    void criaOsAlunosDaSementeComRosto() throws Exception {
        loader(SEMENTE).carregar();

        var um = alunos.findByCodigoPublico("ALU-9001").orElseThrow();
        assertThat(um.isAtivo()).isTrue();
        assertThat(um.getMatricula()).isEqualTo("20269001");
        assertThat(um.getNome()).isEqualTo("Aluno Ficticio Um");
        assertThat(um.temRostoCadastrado()).isTrue();
        assertThat(objectMapper.readTree(um.getRostoEmbeddingJson()).size()).isEqualTo(3);
        assertThat(alunos.findByCodigoPublico("ALU-9002")).isPresent();
    }

    @Test
    void segundaCargaNaoDuplicaNemSobrescreveEmbedding() {
        loader(SEMENTE).carregar();
        String antes = alunos.findByCodigoPublico("ALU-9001").orElseThrow().getRostoEmbeddingJson();
        loader(SEMENTE).carregar();
        em.flush();

        assertThat(alunos.findAll()).hasSize(2);
        assertThat(alunos.findByCodigoPublico("ALU-9001").orElseThrow().getRostoEmbeddingJson())
            .isEqualTo(antes);
    }

    @Test
    void naoSobrescreveRostoDeAlunoExistente() {
        var existente = new Aluno(UUID.randomUUID(), "ALU-9001", "20269001", "Outro Nome", true, null,
            Instant.now());
        existente.cadastrarRosto("[1.0,0.0]");
        alunos.saveAndFlush(existente);

        loader(SEMENTE).carregar();

        var lido = alunos.findByCodigoPublico("ALU-9001").orElseThrow();
        assertThat(lido.getRostoEmbeddingJson()).isEqualTo("[1.0,0.0]");
        assertThat(lido.getNome()).isEqualTo("Outro Nome");
    }

    @Test
    void preencheRostoDeAlunoExistenteSemRosto() {
        alunos.saveAndFlush(new Aluno(UUID.randomUUID(), "ALU-9001", "20269001", "Outro Nome", true,
            null, Instant.now()));

        loader(SEMENTE).carregar();

        assertThat(alunos.findAll()).hasSize(2);
        assertThat(alunos.findByCodigoPublico("ALU-9001").orElseThrow().temRostoCadastrado()).isTrue();
    }

    @Test
    void matriculaDeOutroCodigoPulaSoEsseItem() {
        alunos.saveAndFlush(new Aluno(UUID.randomUUID(), "ALU-0001", "20269001", "Dono da Matrícula",
            true, null, Instant.now()));

        loader(SEMENTE).carregar();
        em.flush();

        assertThat(alunos.findByCodigoPublico("ALU-9001")).isEmpty();
        var dono = alunos.findByCodigoPublico("ALU-0001").orElseThrow();
        assertThat(dono.getNome()).isEqualTo("Dono da Matrícula");
        assertThat(dono.temRostoCadastrado()).isFalse();
        assertThat(alunos.findByCodigoPublico("ALU-9002").orElseThrow().temRostoCadastrado()).isTrue();
    }

    @Test
    void recursoAusenteNaoFalha() {
        assertThatCode(() -> loader(new ClassPathResource("demo/nao-existe.json")).carregar())
            .doesNotThrowAnyException();
        assertThat(alunos.findAll()).isEmpty();
    }
}
