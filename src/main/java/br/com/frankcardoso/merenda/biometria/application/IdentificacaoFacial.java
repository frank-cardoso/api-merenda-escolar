package br.com.frankcardoso.merenda.biometria.application;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;

/** {@code aluno} so vem preenchido em RECONHECIDO e AMBIGUO (o mais proximo). */
public record IdentificacaoFacial(Resultado resultado, Aluno aluno) {
    public enum Resultado { RECONHECIDO, NAO_RECONHECIDO, AMBIGUO, SEM_CANDIDATOS }
}
