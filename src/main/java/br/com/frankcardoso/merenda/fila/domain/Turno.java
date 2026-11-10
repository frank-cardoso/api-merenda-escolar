package br.com.frankcardoso.merenda.fila.domain;

public enum Turno {
    MANHA,
    TARDE,
    NOITE,

    /**
     * Alunos de tempo integral. Diferente dos outros, nao e uma faixa de horario: e a
     * modalidade da turma, que na fila da direito as refeicoes da manha e da tarde (a grade de
     * refeicoes diz quais). Espelha a coluna QTD_INTEGRAL do sistema legado — onde, por sinal,
     * e o turno com mais registros, enquanto NOITE nunca foi usado.
     */
    INTEGRAL
}
