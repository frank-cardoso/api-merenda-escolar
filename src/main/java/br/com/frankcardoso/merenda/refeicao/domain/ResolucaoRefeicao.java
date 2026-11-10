package br.com.frankcardoso.merenda.refeicao.domain;

/** {@code refeicaoNaJanela} só vem preenchida no bloqueio por turma: a refeição que o horário pede, mas a turma não tem. */
public record ResolucaoRefeicao(TipoRefeicao refeicao, String motivoBloqueio, TipoRefeicao refeicaoNaJanela) {

    public static ResolucaoRefeicao autorizada(TipoRefeicao refeicao) {
        return new ResolucaoRefeicao(refeicao, null, null);
    }

    public static ResolucaoRefeicao bloqueada(String motivo) {
        return new ResolucaoRefeicao(null, motivo, null);
    }

    public static ResolucaoRefeicao naoPrevista(TipoRefeicao naJanela) {
        return new ResolucaoRefeicao(null, GradeRefeicoes.NAO_PREVISTA_PARA_TURMA, naJanela);
    }

    public boolean bloqueada() {
        return motivoBloqueio != null;
    }
}
