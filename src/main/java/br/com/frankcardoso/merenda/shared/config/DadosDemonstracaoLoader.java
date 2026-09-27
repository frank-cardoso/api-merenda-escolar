package br.com.frankcardoso.merenda.shared.config;

import br.com.frankcardoso.merenda.aluno.domain.Aluno;
import br.com.frankcardoso.merenda.aluno.infrastructure.AlunoRepository;
import br.com.frankcardoso.merenda.cardapio.domain.Cardapio;
import br.com.frankcardoso.merenda.cardapio.infrastructure.CardapioRepository;
import br.com.frankcardoso.merenda.catalogo.infrastructure.ReceitaRepository;
import br.com.frankcardoso.merenda.fila.domain.AuditoriaConsumo;
import br.com.frankcardoso.merenda.fila.domain.MetodoIdentificacao;
import br.com.frankcardoso.merenda.fila.domain.ResultadoConsumo;
import br.com.frankcardoso.merenda.fila.domain.Turno;
import br.com.frankcardoso.merenda.fila.infrastructure.AuditoriaConsumoRepository;
import br.com.frankcardoso.merenda.medicao.domain.MedicaoSobra;
import br.com.frankcardoso.merenda.medicao.infrastructure.MedicaoSobraRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Popula alunos, cardapios e auditoria de consumo dos ultimos dias, para o dashboard mostrar
 * um cenario com numeros em vez de zeros.
 *
 * Necessario porque o dashboard conta consumo em auditoria_consumo (alimentada pela fila, um
 * registro por bipagem de QR code), e nao no historico sintetico — que serve so ao prompt da IA.
 * Sem isso, demonstrar o dashboard exigiria bipar centenas de QR codes na mao.
 *
 * A taxa de adesao reproduz a medida em producao (cerca de 31%): 300 refeicoes planejadas
 * resultam em torno de 93 consumos. E deliberadamente um cenario de desperdicio alto, porque e
 * o que o dado real mostra.
 *
 * Idempotente por dia e turno: so gera onde ainda nao existe auditoria, preservando registros
 * criados na mao pela tela da fila.
 */
@Component
@Profile("!test")
@Order(5)
public class DadosDemonstracaoLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DadosDemonstracaoLoader.class);
    private static final ZoneId ZONA_OPERACIONAL = ZoneId.of("America/Sao_Paulo");

    /** Turnos com historico sintetico. NOITE fica de fora: nunca foi usado em producao. */
    private static final Set<Turno> TURNOS_COM_MOVIMENTO =
        EnumSet.of(Turno.MANHA, Turno.TARDE, Turno.INTEGRAL);

    private static final Set<DayOfWeek> DIAS_UTEIS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

    private static final double TAXA_ADESAO = 0.31;
    /**
     * Variacao diaria menor que a observada em producao, de proposito: com o ruido real
     * (cerca de 12 pontos) o efeito do cardapio sobre o consumo fica indistinguivel de
     * acaso, e a correlacao que a analise deve encontrar nao seria detectavel no dado.
     */
    private static final double VARIACAO_ADESAO = 0.04;
    private static final double PROPORCAO_BLOQUEIOS = 0.04;
    private static final String[] MOTIVOS_BLOQUEIO = {"CONSUMO_JA_REGISTRADO", "ALUNO_NAO_ENCONTRADO_OU_INATIVO"};

    private final AlunoRepository alunos;
    private final CardapioRepository cardapios;
    private final AuditoriaConsumoRepository auditorias;
    private final MedicaoSobraRepository medicoes;
    private final CatalogoItensDemonstracao catalogo;
    private final ReceitaRepository receitas;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int diasDeHistorico;
    private final int alunosNecessarios;

    public DadosDemonstracaoLoader(
        AlunoRepository alunos,
        CardapioRepository cardapios,
        AuditoriaConsumoRepository auditorias,
        MedicaoSobraRepository medicoes,
        CatalogoItensDemonstracao catalogo,
        ReceitaRepository receitas,
        ObjectMapper objectMapper,
        Clock clock,
        // Mantém dados suficientes para consultar qualquer referência dos últimos 30 dias
        // sem deixar a demonstração sem um cardápio analisável no início da janela.
        @Value("${merenda.demo.dias:60}") int diasDeHistorico,
        @Value("${merenda.demo.alunos:150}") int alunosNecessarios
    ) {
        this.alunos = alunos;
        this.cardapios = cardapios;
        this.auditorias = auditorias;
        this.medicoes = medicoes;
        this.catalogo = catalogo;
        this.receitas = receitas;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.diasDeHistorico = diasDeHistorico;
        this.alunosNecessarios = alunosNecessarios;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void carregar() {
        if (diasDeHistorico <= 0) return;

        var rng = new Random(2026);
        Instant agora = Instant.now(clock);
        List<Aluno> turma = garantirAlunos(agora);
        LocalDate hoje = LocalDate.now(clock.withZone(ZONA_OPERACIONAL));

        corrigirCardapiosDemonstracao(rng);

        long consumosCriados = 0;
        for (int voltar = diasDeHistorico; voltar >= 0; voltar--) {
            LocalDate dia = hoje.minusDays(voltar);
            if (!DIAS_UTEIS.contains(dia.getDayOfWeek())) continue;

            for (Turno turno : TURNOS_COM_MOVIMENTO) {
                var cardapio = garantirCardapio(dia, turno, rng);
                consumosCriados += gerarConsumos(cardapio, dia, turno, turma, rng, agora);
            }
        }

        garantirMedicoesCardapioPrincipal(hoje, rng, agora);

        // NOITE ganha cardapio apenas para hoje, e sem consumo: o turno nao existe em producao,
        // mas o TurnoResolver da fila resolve NOITE depois das 18h — sem cardapio, testar a
        // bipagem nesse horario falharia.
        garantirCardapio(hoje, Turno.NOITE, rng);

        if (consumosCriados > 0) {
            LOG.info("Dados de demonstracao: {} consumos gerados nos ultimos {} dias",
                consumosCriados, diasDeHistorico);
        }
    }

    private List<Aluno> garantirAlunos(Instant agora) {
        List<Aluno> existentes = alunos.findAll();
        if (existentes.size() >= alunosNecessarios) return existentes;

        List<Aluno> novos = new ArrayList<>();
        for (int numero = existentes.size() + 1; numero <= alunosNecessarios; numero++) {
            novos.add(new Aluno(
                UUID.randomUUID(),
                "ALU-%04d".formatted(numero),
                "2026%04d".formatted(numero),
                "Aluno Demonstracao %d".formatted(numero),
                "%dA".formatted(1 + (numero % 9)),
                true,
                agora
            ));
        }
        alunos.saveAll(novos);
        LOG.info("Dados de demonstracao: {} alunos criados", novos.size());

        existentes.addAll(novos);
        return existentes;
    }

    private Cardapio garantirCardapio(LocalDate dia, Turno turno, Random rng) {
        var existente = cardapios.findByDataAndTurnoAndAtivoTrue(dia, turno);
        if (existente.isPresent()) {
            return corrigirSeNecessario(existente.get(), rng);
        }

        var itens = turno == Turno.INTEGRAL
            ? catalogo.cardapioPrincipal(rng)
            : catalogo.sortearCardapio(rng);
        return cardapios.save(new Cardapio(
            UUID.randomUUID(), dia, turno,
            itens.nomeDaRefeicao(),
            "Cardapio de demonstracao",
            itens.comoJson(),
            300, true));
    }

    /**
     * Cardapios demonstrativos antigos foram gravados antes da identidade por receita. Eles nao
     * podem continuar sendo preservados como se fossem validos: sem receitaId, historico,
     * medicao e analise ficam sem uma chave comum. Recria somente esses dados controlados pelo
     * seed, mantendo data, turno e vinculo com auditorias existentes.
     */
    private void corrigirCardapiosDemonstracao(Random rng) {
        cardapios.findAll().stream()
            .filter(cardapio -> cardapio.isAtivo())
            .filter(cardapio -> "Cardapio de demonstracao".equals(cardapio.getDescricao()))
            .forEach(cardapio -> corrigirSeNecessario(cardapio, rng));
    }

    private Cardapio corrigirSeNecessario(Cardapio cardapio, Random rng) {
        if (cardapio.getTurno() == Turno.INTEGRAL
            && "Cardapio de demonstracao".equals(cardapio.getDescricao())) {
            var principal = catalogo.cardapioPrincipal(rng);
            if (!mesmoConjuntoDeReceitas(cardapio.getItensJson(), principal.comoJson())) {
                cardapio.atualizar(cardapio.getData(), cardapio.getTurno(), principal.nomeDaRefeicao(),
                    cardapio.getDescricao(), principal.comoJson(), cardapio.getQuantidadePlanejada());
                cardapios.save(cardapio);
                return cardapio;
            }
        }
        if (itensComReceita(cardapio.getItensJson())) return cardapio;

        var itens = catalogo.sortearCardapio(rng);
        cardapio.atualizar(cardapio.getData(), cardapio.getTurno(), itens.nomeDaRefeicao(),
            cardapio.getDescricao(), itens.comoJson(), cardapio.getQuantidadePlanejada());
        cardapios.save(cardapio);
        LOG.info("Cardapio de demonstracao corrigido: data={}, turno={}, itens com receitaId",
            cardapio.getData(), cardapio.getTurno());
        return cardapio;
    }

    private boolean mesmoConjuntoDeReceitas(String primeiroJson, String segundoJson) {
        try {
            return ids(primeiroJson).equals(ids(segundoJson));
        } catch (Exception exception) {
            return false;
        }
    }

    private Set<UUID> ids(String json) throws Exception {
        var itens = objectMapper.readTree(json);
        var ids = new java.util.HashSet<UUID>();
        for (JsonNode item : itens) ids.add(UUID.fromString(item.path("receitaId").asText()));
        return ids;
    }

    private void garantirMedicoesCardapioPrincipal(LocalDate hoje, Random rng, Instant registradoEm) {
        for (int voltar = diasDeHistorico; voltar >= 0; voltar--) {
            var dia = hoje.minusDays(voltar);
            if (!DIAS_UTEIS.contains(dia.getDayOfWeek())) continue;
            var cardapio = cardapios.findByDataAndTurnoAndAtivoTrue(dia, Turno.INTEGRAL).orElse(null);
            if (cardapio == null || !"Cardapio de demonstracao".equals(cardapio.getDescricao())) continue;
            try {
                var receitas = ids(cardapio.getItensJson());
                for (var receitaId : receitas) {
                    if (medicoes.findByDataAndTurnoAndEscolaIdAndReceitaId(
                        dia, Turno.INTEGRAL, 1, receitaId).isPresent()) continue;
                    int preparadas = 100;
                    int servidas = 78 + rng.nextInt(15);
                    int resto = Math.min(servidas, 5 + rng.nextInt(12));
                    medicoes.save(new MedicaoSobra(UUID.randomUUID(), dia, Turno.INTEGRAL, 1,
                        receitaId, preparadas, servidas, preparadas - servidas, resto,
                        "SINTETICO", registradoEm));
                }
            } catch (Exception exception) {
                LOG.warn("Não foi possível gerar medições do cardápio principal em {}: {}",
                    dia, exception.getMessage());
            }
        }
    }

    private boolean itensComReceita(String itensJson) {
        try {
            JsonNode itens = objectMapper.readTree(itensJson);
            if (!itens.isArray() || itens.isEmpty()) return false;
            for (JsonNode item : itens) {
                if (!item.path("receitaId").isTextual()
                    || item.path("receitaId").asText().isBlank()) return false;
                var receitaId = UUID.fromString(item.path("receitaId").asText());
                var receita = receitas.findById(receitaId).orElse(null);
                if (receita == null || !receita.getNome().equals(item.path("nome").asText())) {
                    return false;
                }
            }
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private long gerarConsumos(Cardapio cardapio, LocalDate dia, Turno turno, List<Aluno> turma,
                               Random rng, Instant agora) {
        // Idempotencia: se o dia e turno ja tem movimento, nao mexe. Preserva o que a tela da
        // fila registrou na mao.
        if (auditorias.countByDataOperacionalAndTurnoAndResultado(
            dia, turno, ResultadoConsumo.AUTORIZADO) > 0) {
            return 0;
        }

        double variacao = (rng.nextDouble() * 2 - 1) * VARIACAO_ADESAO;
        double adesao = (TAXA_ADESAO + variacao) * catalogo.fatorDeAdesao(nomesDosItens(cardapio));
        int autorizados = (int) Math.round(
            cardapio.getQuantidadePlanejada() * Math.min(0.95, Math.max(0.05, adesao)));
        autorizados = Math.min(autorizados, turma.size());
        int bloqueados = (int) Math.round(autorizados * PROPORCAO_BLOQUEIOS);

        List<Aluno> embaralhados = new ArrayList<>(turma);
        java.util.Collections.shuffle(embaralhados, rng);

        List<AuditoriaConsumo> registros = new ArrayList<>(autorizados + bloqueados);
        for (int indice = 0; indice < autorizados; indice++) {
            Aluno aluno = embaralhados.get(indice);
            registros.add(criar(aluno, cardapio, dia, turno, rng, ResultadoConsumo.AUTORIZADO, null,
                chave(aluno, dia, turno)));
        }

        // Bloqueios reusam alunos que ja consumiram — o motivo mais comum na fila e justamente
        // a segunda tentativa no mesmo turno. Chave nula porque a unica cabe so ao autorizado.
        for (int indice = 0; indice < bloqueados && indice < autorizados; indice++) {
            Aluno aluno = embaralhados.get(indice);
            registros.add(criar(aluno, cardapio, dia, turno, rng, ResultadoConsumo.BLOQUEADO,
                MOTIVOS_BLOQUEIO[rng.nextInt(MOTIVOS_BLOQUEIO.length)], null));
        }

        auditorias.saveAll(registros);
        return registros.size();
    }

    private AuditoriaConsumo criar(Aluno aluno, Cardapio cardapio, LocalDate dia, Turno turno,
                                   Random rng, ResultadoConsumo resultado, String motivo,
                                   String chave) {
        return new AuditoriaConsumo(
            UUID.randomUUID(),
            aluno,
            cardapio,
            dia,
            turno,
            instanteNoTurno(dia, turno, rng),
            rng.nextInt(10) == 0 ? MetodoIdentificacao.FACIAL : MetodoIdentificacao.QR_CODE,
            resultado,
            motivo,
            UUID.randomUUID(),
            chave
        );
    }

    /**
     * Extrai os nomes dos itens do JSON do cardapio. Leitura simples por marcador em vez de
     * desserializacao completa: aqui so os nomes importam, e o formato e sempre o gerado por
     * CatalogoItensDemonstracao.
     */
    private List<String> nomesDosItens(Cardapio cardapio) {
        String json = cardapio.getItensJson();
        if (json == null || json.isBlank()) return List.of();

        List<String> nomes = new ArrayList<>();
        String marcador = "\"nome\":\"";
        int posicao = json.indexOf(marcador);
        while (posicao >= 0) {
            int inicio = posicao + marcador.length();
            int fim = json.indexOf('"', inicio);
            if (fim < 0) break;
            nomes.add(json.substring(inicio, fim));
            posicao = json.indexOf(marcador, fim);
        }
        return nomes;
    }

    private String chave(Aluno aluno, LocalDate dia, Turno turno) {
        return aluno.getId() + ":" + dia + ":" + turno;
    }

    private Instant instanteNoTurno(LocalDate dia, Turno turno, Random rng) {
        int horaBase = switch (turno) {
            case MANHA -> 9;
            case TARDE -> 14;
            case INTEGRAL -> 12;
            case NOITE -> 19;
        };
        LocalTime horario = LocalTime.of(horaBase, rng.nextInt(60));
        return dia.atTime(horario).atZone(ZONA_OPERACIONAL).toInstant();
    }
}
