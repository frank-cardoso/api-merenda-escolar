package br.com.frankcardoso.merenda.relatorio.domain;

import br.com.frankcardoso.merenda.fila.domain.Turno;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "relatorio_ia")
public class RelatorioIA {

    @Id
    private UUID id;

    @Column(name = "data_referencia", nullable = false)
    private LocalDate dataReferencia;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Turno turno;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusRelatorioIA status;

    @Column(length = 40)
    private String provedor;

    @Column(length = 100)
    private String modelo;

    @Column(name = "prompt_versao", nullable = false, length = 30)
    private String promptVersao;

    @Column(name = "dados_entrada_json", columnDefinition = "CHARACTER LARGE OBJECT")
    private String dadosEntradaJson;

    @Column(name = "resultado_json", columnDefinition = "CHARACTER LARGE OBJECT")
    private String resultadoJson;

    @Column(columnDefinition = "CHARACTER LARGE OBJECT")
    private String resumo;

    @Column(columnDefinition = "CHARACTER LARGE OBJECT")
    private String erro;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "iniciado_em")
    private Instant iniciadoEm;

    @Column(name = "concluido_em")
    private Instant concluidoEm;

    @Version
    @Column(nullable = false)
    private long versao;

    protected RelatorioIA() {
    }

    public static RelatorioIA pendente(LocalDate data, Turno turno, Instant agora) {
        var relatorio = new RelatorioIA();
        relatorio.id = UUID.randomUUID();
        relatorio.dataReferencia = data;
        relatorio.turno = turno;
        relatorio.status = StatusRelatorioIA.PENDENTE;
        relatorio.promptVersao = "v1";
        relatorio.tentativas = 0;
        relatorio.criadoEm = agora;
        return relatorio;
    }

    public void iniciar(String provedor, String modelo, String dadosEntradaJson, Instant agora) {
        if (status != StatusRelatorioIA.PENDENTE && status != StatusRelatorioIA.FALHOU) {
            throw new IllegalStateException("Relatorio nao pode ser iniciado no estado " + status);
        }
        this.status = StatusRelatorioIA.PROCESSANDO;
        this.provedor = provedor;
        this.modelo = modelo;
        this.dadosEntradaJson = dadosEntradaJson;
        this.erro = null;
        this.tentativas++;
        this.iniciadoEm = agora;
        this.concluidoEm = null;
    }

    public void concluir(String resultadoJson, String resumo, Instant agora) {
        exigirProcessando();
        this.status = StatusRelatorioIA.CONCLUIDO;
        this.resultadoJson = resultadoJson;
        this.resumo = resumo;
        this.concluidoEm = agora;
    }

    public void falhar(String erro, Instant agora) {
        this.status = StatusRelatorioIA.FALHOU;
        this.erro = erro;
        this.concluidoEm = agora;
    }

    private void exigirProcessando() {
        if (status != StatusRelatorioIA.PROCESSANDO) {
            throw new IllegalStateException("Relatorio nao esta em processamento");
        }
    }

    public UUID getId() { return id; }
    public LocalDate getDataReferencia() { return dataReferencia; }
    public Turno getTurno() { return turno; }
    public StatusRelatorioIA getStatus() { return status; }
    public String getProvedor() { return provedor; }
    public String getModelo() { return modelo; }
    public String getPromptVersao() { return promptVersao; }
    public String getResultadoJson() { return resultadoJson; }
    public String getResumo() { return resumo; }
    public String getErro() { return erro; }
    public int getTentativas() { return tentativas; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getIniciadoEm() { return iniciadoEm; }
    public Instant getConcluidoEm() { return concluidoEm; }
}
