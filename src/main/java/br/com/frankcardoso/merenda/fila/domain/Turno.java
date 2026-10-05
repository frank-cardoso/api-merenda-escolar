package br.com.frankcardoso.merenda.fila.domain;

public enum Turno {
    MANHA,
    TARDE,
    NOITE,

    /**
     * Alunos de tempo integral. Diferente dos outros, nao e uma faixa de horario: e a
     * modalidade do aluno, e por isso o {@code TurnoResolver} (que resolve pelo relogio da
     * catraca) nunca retorna INTEGRAL. Serve para planejamento de cardapio e para o historico,
     * espelhando a coluna QTD_INTEGRAL do sistema legado — onde, por sinal, e o turno com mais
     * registros, enquanto NOITE nunca foi usado.
     */
    INTEGRAL
}
