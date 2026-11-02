package br.com.frankcardoso.merenda.biometria.application.port;

import java.util.List;

/**
 * Fronteira com o servico de reconhecimento facial (Python/DeepFace).
 *
 * So gera e compara vetores; nunca guarda foto nem embedding — quem guarda e o Java, no
 * cadastro do aluno. Verificacao e sempre 1:1 (contra um aluno ja identificado), nunca busca
 * entre todos os alunos.
 */
public interface ReconhecimentoFacialPort {

    List<Float> gerarEmbedding(String fotoBase64);

    ResultadoVerificacaoFacial verificar(String fotoBase64, List<Float> embeddingCadastrado);

    record ResultadoVerificacaoFacial(boolean confere, double distancia, double limiar, String modelo) {
    }
}
