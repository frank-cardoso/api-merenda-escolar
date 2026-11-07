package br.com.frankcardoso.merenda.biometria.application.port;

import java.util.List;
import java.util.UUID;

/**
 * Fronteira com o servico de reconhecimento facial (Python/DeepFace).
 *
 * So gera e compara vetores; nunca guarda foto nem embedding — quem guarda e o Java, no
 * cadastro do aluno. Oferece identificacao 1:N: compara a foto com os candidatos que o Java
 * envia e devolve a decisao, sem consultar banco.
 */
public interface ReconhecimentoFacialPort {

    List<Float> gerarEmbedding(String fotoBase64);

    ResultadoIdentificacao identificar(String fotoBase64, List<Candidato> candidatos);

    record Candidato(UUID alunoId, List<Float> embedding) {
    }

    record ResultadoIdentificacao(Decisao decisao, UUID melhorCandidatoId, Double distancia,
                                  Double distanciaSegundo, double limiar, int candidatosIgnorados) {
    }

    enum Decisao { RECONHECIDO, NAO_RECONHECIDO, AMBIGUO }
}
