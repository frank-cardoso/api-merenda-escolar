package br.com.frankcardoso.merenda.biometria.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.biometria.application.IdentificacaoFacial.Resultado;
import br.com.frankcardoso.merenda.biometria.application.port.ReconhecimentoFacialPort;
import br.com.frankcardoso.merenda.biometria.application.port.ReconhecimentoFacialPort.Candidato;
import br.com.frankcardoso.merenda.biometria.application.port.ReconhecimentoFacialPort.Decisao;
import br.com.frankcardoso.merenda.biometria.application.port.ReconhecimentoFacialPort.ResultadoIdentificacao;
import br.com.frankcardoso.merenda.biometria.domain.ReconhecimentoFacialIndisponivelException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdentificacaoFacialServiceTest {

    private static final String FOTO = "foto-base64";

    @Mock private ReconhecimentoFacialPort port;
    @Mock private AlunoRepository alunoRepository;

    private IdentificacaoFacialService service;

    @BeforeEach
    void preparar() {
        service = new IdentificacaoFacialService(port, alunoRepository, new ObjectMapper());
    }

    private static Aluno aluno(String codigo, String embeddingJson) {
        Aluno aluno = new Aluno(UUID.randomUUID(), codigo, "M-" + codigo, "Aluno " + codigo, true, null,
            Instant.parse("2026-01-01T00:00:00Z"));
        if (embeddingJson != null) aluno.cadastrarRosto(embeddingJson);
        return aluno;
    }

    private static ResultadoIdentificacao resultado(Decisao decisao, UUID melhor) {
        return new ResultadoIdentificacao(decisao, melhor, 0.1, 0.5, 0.4, 0);
    }

    @Test
    void semAlunoComRostoDevolveSemCandidatosSemChamarOPython() {
        when(alunoRepository.findByAtivoTrueAndRostoEmbeddingJsonIsNotNull()).thenReturn(List.of());

        IdentificacaoFacial identificacao = service.identificar(FOTO);

        assertThat(identificacao.resultado()).isEqualTo(Resultado.SEM_CANDIDATOS);
        assertThat(identificacao.aluno()).isNull();
        verifyNoInteractions(port);
    }

    @Test
    void reconhecidoDevolveOAluno() {
        Aluno a = aluno("ALU-9001", "[1.0, 0.0]");
        Aluno b = aluno("ALU-9002", "[0.0, 1.0]");
        when(alunoRepository.findByAtivoTrueAndRostoEmbeddingJsonIsNotNull()).thenReturn(List.of(a, b));
        when(port.identificar(eq(FOTO), anyList())).thenReturn(resultado(Decisao.RECONHECIDO, b.getId()));

        IdentificacaoFacial identificacao = service.identificar(FOTO);

        assertThat(identificacao.resultado()).isEqualTo(Resultado.RECONHECIDO);
        assertThat(identificacao.aluno()).isSameAs(b);
    }

    @Test
    void ambiguoDevolveResultadoAmbiguo() {
        Aluno a = aluno("ALU-9001", "[1.0, 0.0]");
        Aluno b = aluno("ALU-9002", "[0.9, 0.1]");
        when(alunoRepository.findByAtivoTrueAndRostoEmbeddingJsonIsNotNull()).thenReturn(List.of(a, b));
        when(port.identificar(eq(FOTO), anyList())).thenReturn(resultado(Decisao.AMBIGUO, a.getId()));

        IdentificacaoFacial identificacao = service.identificar(FOTO);

        assertThat(identificacao.resultado()).isEqualTo(Resultado.AMBIGUO);
        assertThat(identificacao.aluno()).isSameAs(a);
    }

    @Test
    void naoReconhecidoDevolveSemAluno() {
        Aluno a = aluno("ALU-9001", "[1.0, 0.0]");
        when(alunoRepository.findByAtivoTrueAndRostoEmbeddingJsonIsNotNull()).thenReturn(List.of(a));
        when(port.identificar(eq(FOTO), anyList()))
            .thenReturn(new ResultadoIdentificacao(Decisao.NAO_RECONHECIDO, null, 0.9, null, 0.4, 0));

        IdentificacaoFacial identificacao = service.identificar(FOTO);

        assertThat(identificacao.resultado()).isEqualTo(Resultado.NAO_RECONHECIDO);
        assertThat(identificacao.aluno()).isNull();
    }

    @Test
    void enviaSoOsAlunosComRostoComOEmbeddingConvertido() {
        Aluno a = aluno("ALU-9001", "[1.0, 0.0]");
        Aluno b = aluno("ALU-9002", "[0.0, 0.5]");
        when(alunoRepository.findByAtivoTrueAndRostoEmbeddingJsonIsNotNull()).thenReturn(List.of(a, b));
        when(port.identificar(eq(FOTO), anyList())).thenReturn(resultado(Decisao.NAO_RECONHECIDO, null));

        service.identificar(FOTO);

        ArgumentCaptor<List<Candidato>> captor = ArgumentCaptor.captor();
        verify(port).identificar(eq(FOTO), captor.capture());
        assertThat(captor.getValue()).containsExactly(
            new Candidato(a.getId(), List.of(1.0f, 0.0f)),
            new Candidato(b.getId(), List.of(0.0f, 0.5f)));
    }

    @Test
    void embeddingIlegivelNuloOuVazioEPuladoEOValidoSegueComparado() {
        Aluno quebrado = aluno("ALU-9001", "{nao-e-json");
        Aluno vazio = aluno("ALU-9002", "[]");
        Aluno nulo = aluno("ALU-9003", "null");
        Aluno valido = aluno("ALU-9004", "[1.0, 0.0]");
        when(alunoRepository.findByAtivoTrueAndRostoEmbeddingJsonIsNotNull())
            .thenReturn(List.of(quebrado, vazio, nulo, valido));
        when(port.identificar(eq(FOTO), anyList())).thenReturn(resultado(Decisao.RECONHECIDO, valido.getId()));

        IdentificacaoFacial identificacao = service.identificar(FOTO);

        assertThat(identificacao.resultado()).isEqualTo(Resultado.RECONHECIDO);
        assertThat(identificacao.aluno()).isSameAs(valido);
        ArgumentCaptor<List<Candidato>> captor = ArgumentCaptor.captor();
        verify(port).identificar(eq(FOTO), captor.capture());
        assertThat(captor.getValue()).containsExactly(new Candidato(valido.getId(), List.of(1.0f, 0.0f)));
    }

    @Test
    void soEmbeddingsInutilizaveisSeComportaComoSemCandidatos() {
        Aluno quebrado = aluno("ALU-9001", "{nao-e-json");
        when(alunoRepository.findByAtivoTrueAndRostoEmbeddingJsonIsNotNull()).thenReturn(List.of(quebrado));

        assertThat(service.identificar(FOTO).resultado()).isEqualTo(Resultado.SEM_CANDIDATOS);
        assertThat(service.alunoComMesmoRosto(FOTO, UUID.randomUUID())).isEmpty();
        verifyNoInteractions(port);
    }

    @Test
    void alunoComMesmoRostoIgnoraOProprioAluno() {
        Aluno proprio = aluno("ALU-9001", "[1.0, 0.0]");
        Aluno outro = aluno("ALU-9002", "[0.0, 1.0]");
        when(alunoRepository.findByAtivoTrueAndRostoEmbeddingJsonIsNotNull()).thenReturn(List.of(proprio, outro));
        when(port.identificar(eq(FOTO), anyList())).thenReturn(resultado(Decisao.NAO_RECONHECIDO, null));

        service.alunoComMesmoRosto(FOTO, proprio.getId());

        ArgumentCaptor<List<Candidato>> captor = ArgumentCaptor.captor();
        verify(port).identificar(eq(FOTO), captor.capture());
        assertThat(captor.getValue()).extracting(Candidato::alunoId)
            .containsExactly(outro.getId())
            .doesNotContain(proprio.getId());
    }

    @Test
    void alunoComMesmoRostoComUnicoCandidatoSendoOProprioDevolveVazio() {
        Aluno proprio = aluno("ALU-9001", "[1.0, 0.0]");
        when(alunoRepository.findByAtivoTrueAndRostoEmbeddingJsonIsNotNull()).thenReturn(List.of(proprio));

        Optional<Aluno> encontrado = service.alunoComMesmoRosto(FOTO, proprio.getId());

        assertThat(encontrado).isEmpty();
        verifyNoInteractions(port);
    }

    @Test
    void alunoComMesmoRostoAbaixoDoLimiarDevolveOOutroAluno() {
        Aluno proprio = aluno("ALU-9001", "[1.0, 0.0]");
        Aluno outro = aluno("ALU-9002", "[0.9, 0.1]");
        Aluno terceiro = aluno("ALU-9003", "[0.8, 0.2]");
        when(alunoRepository.findByAtivoTrueAndRostoEmbeddingJsonIsNotNull())
            .thenReturn(List.of(proprio, outro, terceiro));
        when(port.identificar(eq(FOTO), anyList())).thenReturn(resultado(Decisao.AMBIGUO, outro.getId()));

        assertThat(service.alunoComMesmoRosto(FOTO, proprio.getId())).containsSame(outro);
    }

    @Test
    void alunoComMesmoRostoNaoReconhecidoDevolveVazio() {
        Aluno outro = aluno("ALU-9002", "[0.0, 1.0]");
        when(alunoRepository.findByAtivoTrueAndRostoEmbeddingJsonIsNotNull()).thenReturn(List.of(outro));
        when(port.identificar(eq(FOTO), anyList())).thenReturn(resultado(Decisao.NAO_RECONHECIDO, null));

        assertThat(service.alunoComMesmoRosto(FOTO, UUID.randomUUID())).isEmpty();
    }

    @Test
    void identificarComCandidatoDesconhecidoNoPortLancaIndisponivel() {
        Aluno a = aluno("ALU-9001", "[1.0, 0.0]");
        when(alunoRepository.findByAtivoTrueAndRostoEmbeddingJsonIsNotNull()).thenReturn(List.of(a));
        when(port.identificar(eq(FOTO), anyList())).thenReturn(resultado(Decisao.RECONHECIDO, UUID.randomUUID()));

        assertThatThrownBy(() -> service.identificar(FOTO))
            .isInstanceOf(ReconhecimentoFacialIndisponivelException.class);
    }

    @Test
    void alunoComMesmoRostoComCandidatoDesconhecidoNoPortLancaIndisponivel() {
        Aluno outro = aluno("ALU-9002", "[0.0, 1.0]");
        when(alunoRepository.findByAtivoTrueAndRostoEmbeddingJsonIsNotNull()).thenReturn(List.of(outro));
        when(port.identificar(eq(FOTO), anyList())).thenReturn(resultado(Decisao.RECONHECIDO, null));

        assertThatThrownBy(() -> service.alunoComMesmoRosto(FOTO, UUID.randomUUID()))
            .isInstanceOf(ReconhecimentoFacialIndisponivelException.class);
    }
}
