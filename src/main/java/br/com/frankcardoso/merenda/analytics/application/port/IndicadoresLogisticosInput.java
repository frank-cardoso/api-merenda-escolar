package br.com.frankcardoso.merenda.analytics.application.port;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * {@code medicaoDoDia} e nulo enquanto ninguem lancar a sobra do turno. Sem ela nao ha como medir
 * aceitacao nem desperdicio, e o servico devolve os dois como DADOS_INSUFICIENTES em vez de
 * deduzir qualquer coisa da diferenca entre planejado e registrado. Medicao parcial tambem nao
 * serve: aceitacao de um prato nao e aceitacao do cardapio, por isso vai a cobertura junto.
 *
 * {@code receitasMedidas} vai por receita, nao por ingrediente, de proposito. Agregar por
 * ingrediente em SQL escondia a coocorrencia: batata herdava a rejeicao do chuchu por dividirem a
 * sopa de legumes, e o payload ja chegava contaminado. Com o dado por receita mais o grafo, o
 * Python compara presenca contra ausencia e calcula a propria linha de base.
 */
public record IndicadoresLogisticosInput(
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate dataReferencia,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate inicioHistorico, Turno turno,
    int quantidadePlanejada, long consumosRegistrados, long alunosUnicos,
    BigDecimal metaPercentual, List<ContagemTurma> turmas, List<ContagemItem> itens,
    MedicaoDoDia medicaoDoDia, List<ReceitaMedida> receitasMedidas
) {
    public record ContagemTurma(String turma, long consumosRegistrados, long alunosUnicos) {}
    public record ContagemItem(String item, long planejamentos, long execucoesRegistradas,
                               int escolas, List<String> origens) {}
    /** {@code itensMedidos} conta so itens do cardapio: e o numerador da cobertura. */
    public record MedicaoDoDia(long porcoesPreparadas, long porcoesServidas,
                               long sobraNaoDistribuida, long restoNoPrato, int itensMedidos,
                               int itensDoCardapio) {}
    /**
     * Uma receita medida na janela, com sua composicao. O servico Python recebe o grafo no pedido
     * e nao e dono da tabela de ingredientes — e isso que o deixa portavel para outra base.
     */
    public record ReceitaMedida(UUID receitaId, String nome, List<IngredienteRef> ingredientes,
                                long porcoesServidas, long restoNoPrato, long amostra) {}
    public record IngredienteRef(UUID id, String nome) {}
}
