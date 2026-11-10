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
import br.com.frankcardoso.merenda.refeicao.application.GradeRefeicoesService;
import br.com.frankcardoso.merenda.refeicao.domain.RefeicaoPadrao;
import br.com.frankcardoso.merenda.refeicao.domain.TipoRefeicao;
import br.com.frankcardoso.merenda.turma.application.EnturmacaoService;
import br.com.frankcardoso.merenda.turma.domain.Enturmacao;
import br.com.frankcardoso.merenda.turma.domain.Turma;
import br.com.frankcardoso.merenda.turma.infrastructure.EnturmacaoRepository;
import br.com.frankcardoso.merenda.turma.infrastructure.TurmaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * A adesao mira o atendimento por refeicao (metaDeAtendimento): Almoco 93% e Lanche da manha 90%
 * do planejado, sem acao de planejamento na analise; Cafe da manha 62% e Lanche da tarde 68%,
 * que geram a acao de rever a quantidade planejada. Os perfis de receita dao aceitacao baixa a
 * verduras e sobra na panela a arroz, macarrao e pao.
 *
 * As turmas 1A-3A sao da manha, 4A-6A da tarde e 7A-9A integrais; cada aluno so consome nas
 * refeicoes que a grade preve para o turno da turma dele.
 *
 * Idempotente por dia e refeicao: so gera onde ainda nao existe auditoria, preservando registros
 * criados na mao pela tela da fila.
 */
@Component
@Profile("!test")
public class DadosDemonstracaoLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DadosDemonstracaoLoader.class);
    private static final int TURMAS_SINTETICAS = 9;
    private static final int CAPACIDADE_TURMA = 30;
    /** Turma integral: o aluno com rosto passa na demonstração facial em qualquer refeição diurna. */
    private static final int TURMA_DOS_ROSTOS = 7;
    private static final String DESCRICAO_CARDAPIO = "Cardapio de demonstracao";

    private static final Set<DayOfWeek> DIAS_UTEIS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

    /**
     * Fracao dos alunos elegiveis que mira o atendimento (AUTORIZADO, repeticoes incluidas) por
     * refeicao: almoco e lanche da manha ficam acima de 80% do planejado, cafe da manha e lanche da
     * tarde abaixo, para a analise ter um caso de "rever a quantidade planejada" e outro sem acao.
     */
    private static double metaDeAtendimento(UUID tipoRefeicaoId) {
        var padrao = RefeicaoPadrao.doId(tipoRefeicaoId).orElseThrow();
        return switch (padrao) {
            case ALMOCO -> 0.93;
            case LANCHE_DA_MANHA -> 0.90;
            case CAFE_DA_MANHA -> 0.62;
            case LANCHE_DA_TARDE -> 0.68;
            case LANCHE_DA_NOITE -> 0.75;
        };
    }

    /** Servidas e resto no prato (faixa de percentual do servido) de uma receita, para 100 preparadas. */
    private enum PerfilDeReceita {
        POUCO_ACEITA(90, 7, 0.20, 0.30),
        PRODUCAO_ACIMA_DO_CONSUMO(75, 11, 0.03, 0.08),
        COMUM(92, 7, 0.03, 0.08);

        private final int servidasMinimas;
        private final int amplitude;
        private final double restoMinimo;
        private final double restoMaximo;

        PerfilDeReceita(int servidasMinimas, int amplitude, double restoMinimo, double restoMaximo) {
            this.servidasMinimas = servidasMinimas;
            this.amplitude = amplitude;
            this.restoMinimo = restoMinimo;
            this.restoMaximo = restoMaximo;
        }

        static PerfilDeReceita doNome(String nome) {
            String normalizado = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase();
            if (List.of("beterraba", "chuchu", "couve", "brocolis", "repolho", "cenoura").stream()
                .anyMatch(normalizado::contains)) return POUCO_ACEITA;
            if (List.of("arroz", "macarrao", "pao").stream().anyMatch(normalizado::contains)) {
                return PRODUCAO_ACIMA_DO_CONSUMO;
            }
            return COMUM;
        }

        int sortearServidas(Random rng) {
            return servidasMinimas + rng.nextInt(amplitude);
        }

        int sortearResto(int servidas, Random rng) {
            return (int) Math.round(servidas * (restoMinimo + rng.nextDouble() * (restoMaximo - restoMinimo)));
        }
    }
    /**
     * Variacao diaria menor que a observada em producao, de proposito: com o ruido real
     * (cerca de 12 pontos) o efeito do cardapio sobre o consumo fica indistinguivel de
     * acaso, e a correlacao que a analise deve encontrar nao seria detectavel no dado.
     */
    private static final double VARIACAO_ADESAO = 0.05;
    /**
     * Quanto do desvio do fator de adesao do cardapio chega ao consumo. Integral, um cardapio com
     * dois itens de baixa execucao cortaria o atendimento pela metade e tiraria a media da
     * refeicao da faixa que a demonstracao quer mostrar; assim o corte fica em torno de 10%.
     */
    private static final double PESO_DO_CARDAPIO = 0.2;
    private static final double PROPORCAO_BLOQUEIOS = 0.04;
    /**
     * Fracao dos alunos autorizados que bipa uma segunda vez (apos a janela de bloqueio da fila),
     * por serie da turma: 1A-3A repetem mais, 7A-9A quase nao. O padrao e deterministico por turma
     * de proposito: o relatorio de IA narra as repeticoes, e a diferenca entre turmas precisa ter
     * uma explicacao defensavel (alunos menores repetem o prato e perdem a primeira leitura com
     * mais frequencia), nao ser fruto de sorteio uniforme.
     */
    private static double taxaDeRepeticao(String codigoDaTurma) {
        String digitos = codigoDaTurma == null ? "" : codigoDaTurma.replaceAll("\\D", "");
        int serie = digitos.isEmpty() ? 0 : Integer.parseInt(digitos);
        if (serie >= 1 && serie <= 3) return 0.30;
        if (serie >= 4 && serie <= 6) return 0.15;
        if (serie >= 7 && serie <= 9) return 0.05;
        return 0;
    }

    private static final String[] MOTIVOS_BLOQUEIO = {"CONSUMO_JA_REGISTRADO", "ALUNO_NAO_ENCONTRADO_OU_INATIVO"};

    private final AlunoRepository alunos;
    private final CardapioRepository cardapios;
    private final AuditoriaConsumoRepository auditorias;
    private final MedicaoSobraRepository medicoes;
    private final CatalogoItensDemonstracao catalogo;
    private final ReceitaRepository receitas;
    private final TurmaRepository turmas;
    private final EnturmacaoRepository enturmacoes;
    private final EnturmacaoService enturmacaoService;
    private final GradeRefeicoesService grade;
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
        TurmaRepository turmas,
        EnturmacaoRepository enturmacoes,
        EnturmacaoService enturmacaoService,
        GradeRefeicoesService grade,
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
        this.turmas = turmas;
        this.enturmacoes = enturmacoes;
        this.enturmacaoService = enturmacaoService;
        this.grade = grade;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.diasDeHistorico = diasDeHistorico;
        this.alunosNecessarios = alunosNecessarios;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(6)
    @Transactional
    public void carregar() {
        if (diasDeHistorico <= 0) return;

        var rng = new Random(2026);
        Instant agora = Instant.now(clock);
        LocalDate hoje = LocalDate.now(clock.withZone(ApplicationConfig.ZONA_OPERACIONAL));
        // Turmas e enturmacoes precisam existir antes de qualquer consumo: a auditoria nasce
        // com a turma vigente carimbada, e consumo gerado antes do vinculo cairia em "Sem turma".
        Map<String, Turma> turmasPorCodigo = garantirTurmas(agora);
        List<Aluno> todos = garantirAlunos(turmasPorCodigo, agora, hoje);
        Map<UUID, List<Enturmacao>> vinculos = carregarVinculos(todos);
        List<TipoRefeicao> refeicoes = grade.listar();
        // O historico sintetico cobre so as cinco refeicoes semeadas, com os turnos semeados:
        // refeicao criada ou turno acrescentado na tela comeca sem passado.
        List<TipoRefeicao> refeicoesPadrao = refeicoes.stream()
            .filter(refeicao -> RefeicaoPadrao.doId(refeicao.getId()).isPresent())
            .toList();
        List<TipoRefeicao> refeicoesComTurma = refeicoesPadrao.stream()
            .filter(refeicao -> turmasPorCodigo.values().stream()
                .anyMatch(t -> turnosSemeados(refeicao).contains(t.getTurno())))
            .toList();

        corrigirCardapiosDemonstracao(rng);

        long consumosCriados = 0;
        for (int voltar = diasDeHistorico; voltar >= 0; voltar--) {
            LocalDate dia = hoje.minusDays(voltar);
            if (!DIAS_UTEIS.contains(dia.getDayOfWeek())) continue;

            for (TipoRefeicao refeicao : refeicoesComTurma) {
                List<Aluno> elegiveis = elegiveis(todos, dia, turnosSemeados(refeicao), vinculos);
                var cardapio = garantirCardapio(dia, refeicao, elegiveis.size(), rng);
                // Quem tem rosto cadastrado e reconhecido ao vivo: consumo sintetico no nome dele
                // poderia bloquear a primeira leitura da apresentacao por "consumo ja registrado".
                List<Aluno> paraConsumo = elegiveis.stream()
                    .filter(aluno -> daDemonstracao(aluno) && !aluno.temRostoCadastrado()).toList();
                consumosCriados += gerarConsumos(cardapio, dia, refeicao, paraConsumo, vinculos, rng, agora);
            }
        }

        garantirMedicoes(hoje, refeicoesPadrao, rng);

        // Hoje ganha cardapio em toda refeicao da grade que tem turno, inclusive no fim de semana e
        // nas sem turma da demonstracao: a fila resolve a refeicao pelo horario, e sem cardapio a
        // bipagem ao vivo falharia. Refeicao sem turnos esta aposentada e a fila nunca a resolve.
        for (TipoRefeicao refeicao : refeicoes) {
            if (refeicao.getTurnos().isEmpty()) continue;
            garantirCardapio(hoje, refeicao, elegiveis(todos, hoje, refeicao.getTurnos(), vinculos).size(), rng);
        }

        if (consumosCriados > 0) {
            LOG.info("Dados de demonstracao: {} consumos gerados nos ultimos {} dias",
                consumosCriados, diasDeHistorico);
        }
    }

    private Map<String, Turma> garantirTurmas(Instant agora) {
        Map<String, Turma> porCodigo = new HashMap<>();
        for (int n = 1; n <= TURMAS_SINTETICAS; n++) {
            String codigo = codigoDaTurma(n);
            Turno turno = turnoDaTurma(n);
            porCodigo.put(codigo, turmas.findByCodigo(codigo).orElseGet(() -> turmas.save(new Turma(
                UUID.randomUUID(), codigo, "Turma " + codigo, turno, CAPACIDADE_TURMA, null, agora))));
        }
        return porCodigo;
    }

    private static Turno turnoDaTurma(int numero) {
        if (numero <= 3) return Turno.MANHA;
        if (numero <= 6) return Turno.TARDE;
        return Turno.INTEGRAL;
    }

    private List<Aluno> garantirAlunos(Map<String, Turma> turmasPorCodigo, Instant agora, LocalDate hoje) {
        List<Aluno> existentes = alunos.findAll();
        if (existentes.size() < alunosNecessarios) {
            existentes.addAll(criarAlunos(existentes.size(), agora));
        }
        garantirEnturmacoes(existentes, turmasPorCodigo, hoje);
        return existentes;
    }

    private List<Aluno> criarAlunos(int jaExistentes, Instant agora) {

        List<Aluno> novos = new ArrayList<>();
        for (int numero = jaExistentes + 1; numero <= alunosNecessarios; numero++) {
            novos.add(new Aluno(
                UUID.randomUUID(),
                "ALU-%04d".formatted(numero),
                "2026%04d".formatted(numero),
                "Aluno Demonstracao %d".formatted(numero),
                true,
                null,
                agora
            ));
        }
        alunos.saveAll(novos);
        LOG.info("Dados de demonstracao: {} alunos criados", novos.size());
        return novos;
    }

    /**
     * Cada aluno sem vinculo entra na turma "%dA" derivada do numero do codigo (1A a 9A), desde
     * antes do primeiro dia do historico; aluno com rosto cadastrado vai para a turma integral. O
     * aluno sem rosto de menor numero entre os recem-enturmados troca para a turma seguinte no meio
     * do intervalo, para a transferencia ter o que mostrar.
     * Tudo passa pelo EnturmacaoService, que guarda o invariante de nao-sobreposicao.
     */
    private void garantirEnturmacoes(List<Aluno> todos, Map<String, Turma> porCodigo, LocalDate hoje) {
        LocalDate inicio = hoje.minusDays(diasDeHistorico + 1L);
        List<Aluno> enturmados = new ArrayList<>();
        for (Aluno aluno : todos) {
            if (!daDemonstracao(aluno)) continue;
            if (!enturmacoes.findByAlunoIdOrderByDataInicioDesc(aluno.getId()).isEmpty()) continue;
            int turma = aluno.temRostoCadastrado()
                ? TURMA_DOS_ROSTOS
                : 1 + numeroDoCodigo(aluno) % TURMAS_SINTETICAS;
            enturmacaoService.enturmar(aluno.getId(), porCodigo.get(codigoDaTurma(turma)).getId(), inicio);
            enturmados.add(aluno);
        }

        enturmados.stream()
            .filter(aluno -> !aluno.temRostoCadastrado())
            .min(Comparator.comparingInt(this::numeroDoCodigo))
            .ifPresent(aluno -> {
                int proxima = 1 + (numeroDoCodigo(aluno) + 1) % TURMAS_SINTETICAS;
                enturmacaoService.transferir(aluno.getId(), porCodigo.get(codigoDaTurma(proxima)).getId(),
                    hoje.minusDays(diasDeHistorico / 2));
            });
    }

    private static String codigoDaTurma(int numero) {
        return "%dA".formatted(numero);
    }

    /** Alunos cadastrados fora da demonstração (código fora de ALU-<número>) ficam intocados. */
    private static boolean daDemonstracao(Aluno aluno) {
        return aluno.getCodigoPublico().matches("ALU-\\d+");
    }

    private int numeroDoCodigo(Aluno aluno) {
        String digitos = aluno.getCodigoPublico().replaceAll("\\D", "");
        return digitos.isEmpty() ? 0 : Integer.parseInt(digitos);
    }

    private Map<UUID, List<Enturmacao>> carregarVinculos(List<Aluno> todos) {
        Map<UUID, List<Enturmacao>> porAluno = new HashMap<>();
        for (Aluno aluno : todos) {
            porAluno.put(aluno.getId(), enturmacoes.findByAlunoIdOrderByDataInicioDesc(aluno.getId()));
        }
        return porAluno;
    }

    private Turma turmaVigente(Aluno aluno, LocalDate dia, Map<UUID, List<Enturmacao>> vinculos) {
        return vinculos.getOrDefault(aluno.getId(), List.of()).stream()
            .filter(vinculo -> vinculo.vigenteEm(dia))
            .findFirst().map(Enturmacao::getTurma).orElse(null);
    }

    private static Set<Turno> turnosSemeados(TipoRefeicao refeicao) {
        return RefeicaoPadrao.doId(refeicao.getId()).orElseThrow().turnos();
    }

    private List<Aluno> elegiveis(List<Aluno> todos, LocalDate dia, Set<Turno> turnos,
                                  Map<UUID, List<Enturmacao>> vinculos) {
        return todos.stream().filter(aluno -> {
            Turma turma = turmaVigente(aluno, dia, vinculos);
            return turma != null && turnos.contains(turma.getTurno());
        }).toList();
    }

    private Cardapio garantirCardapio(LocalDate dia, TipoRefeicao refeicao, int alunosElegiveis, Random rng) {
        var existente = cardapios.findByDataAndTipoRefeicaoIdAndAtivoTrue(dia, refeicao.getId());
        if (existente.isPresent()) {
            return corrigirSeNecessario(existente.get(), rng);
        }

        var itens = ehAlmoco(refeicao.getId())
            ? catalogo.cardapioPrincipal(rng)
            : catalogo.sortearCardapio(rng);
        return cardapios.save(new Cardapio(
            UUID.randomUUID(), dia, refeicao.getId(),
            itens.nomeDaRefeicao(),
            DESCRICAO_CARDAPIO,
            itens.comoJson(),
            Math.max(1, alunosElegiveis), true));
    }

    private static boolean ehAlmoco(UUID tipoRefeicaoId) {
        return RefeicaoPadrao.ALMOCO.id().equals(tipoRefeicaoId);
    }

    /**
     * Cardapios demonstrativos antigos foram gravados antes da identidade por receita. Eles nao
     * podem continuar sendo preservados como se fossem validos: sem receitaId, historico,
     * medicao e analise ficam sem uma chave comum. Recria somente esses dados controlados pelo
     * seed, mantendo data, refeicao e vinculo com auditorias existentes.
     */
    private void corrigirCardapiosDemonstracao(Random rng) {
        cardapios.findAll().stream()
            .filter(cardapio -> cardapio.isAtivo())
            .filter(cardapio -> DESCRICAO_CARDAPIO.equals(cardapio.getDescricao()))
            .forEach(cardapio -> corrigirSeNecessario(cardapio, rng));
    }

    private Cardapio corrigirSeNecessario(Cardapio cardapio, Random rng) {
        if (ehAlmoco(cardapio.getTipoRefeicaoId())
            && DESCRICAO_CARDAPIO.equals(cardapio.getDescricao())) {
            var principal = catalogo.cardapioPrincipal(rng);
            if (!mesmoConjuntoDeReceitas(cardapio.getItensJson(), principal.comoJson())) {
                cardapio.atualizar(cardapio.getData(), cardapio.getTipoRefeicaoId(), principal.nomeDaRefeicao(),
                    cardapio.getDescricao(), principal.comoJson(), cardapio.getQuantidadePlanejada());
                cardapios.save(cardapio);
                return cardapio;
            }
        }
        if (itensComReceita(cardapio.getItensJson())) return cardapio;

        var itens = catalogo.sortearCardapio(rng);
        cardapio.atualizar(cardapio.getData(), cardapio.getTipoRefeicaoId(), itens.nomeDaRefeicao(),
            cardapio.getDescricao(), itens.comoJson(), cardapio.getQuantidadePlanejada());
        cardapios.save(cardapio);
        LOG.info("Cardapio de demonstracao corrigido: data={}, tipoRefeicaoId={}, itens com receitaId",
            cardapio.getData(), cardapio.getTipoRefeicaoId());
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

    /**
     * Todo dia anterior e toda refeicao semeada: cardapio de demonstracao deixado por uma subida
     * anterior (fim de semana, lanche da noite) ficaria pendente para sempre.
     */
    private void garantirMedicoes(LocalDate hoje, List<TipoRefeicao> refeicoes, Random rng) {
        for (int voltar = diasDeHistorico; voltar >= 1; voltar--) {
            var dia = hoje.minusDays(voltar);
            for (TipoRefeicao refeicao : refeicoes) {
                garantirMedicoes(dia, refeicao, rng);
            }
        }
    }

    private void garantirMedicoes(LocalDate dia, TipoRefeicao refeicao, Random rng) {
        var cardapio = cardapios.findByDataAndTipoRefeicaoIdAndAtivoTrue(dia, refeicao.getId()).orElse(null);
        if (cardapio == null || !DESCRICAO_CARDAPIO.equals(cardapio.getDescricao())) return;
        var registradoEm = dia.atTime(refeicao.getFim()).atZone(ApplicationConfig.ZONA_OPERACIONAL).toInstant();
        try {
            for (var item : objectMapper.readTree(cardapio.getItensJson())) {
                var receitaId = UUID.fromString(item.path("receitaId").asText());
                var perfil = PerfilDeReceita.doNome(item.path("nome").asText());
                if (medicoes.findByDataAndTipoRefeicaoIdAndEscolaIdAndReceitaId(
                    dia, refeicao.getId(), 1, receitaId).isPresent()) continue;
                int preparadas = 100;
                int servidas = perfil.sortearServidas(rng);
                int resto = perfil.sortearResto(servidas, rng);
                medicoes.save(new MedicaoSobra(UUID.randomUUID(), dia, refeicao.getId(), 1,
                    receitaId, preparadas, servidas, preparadas - servidas, resto,
                    "SINTETICO", registradoEm));
            }
        } catch (Exception exception) {
            LOG.warn("Não foi possível gerar medições do cardápio em {} ({}): {}",
                dia, refeicao.getNome(), exception.getMessage());
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

    private long gerarConsumos(Cardapio cardapio, LocalDate dia, TipoRefeicao refeicao, List<Aluno> elegiveis,
                               Map<UUID, List<Enturmacao>> vinculos, Random rng, Instant agora) {
        // Idempotencia: se o dia e a refeicao ja tem movimento, nao mexe. Preserva o que a tela da
        // fila registrou na mao.
        if (auditorias.countByDataOperacionalAndTipoRefeicaoIdAndResultado(
            dia, refeicao.getId(), ResultadoConsumo.AUTORIZADO) > 0) {
            return 0;
        }

        double variacao = (rng.nextDouble() * 2 - 1) * VARIACAO_ADESAO;
        // A meta conta tambem as repeticoes; a primeira leitura mira a meta descontada da repeticao
        // esperada dos alunos que consomem, que varia com a serie das turmas da refeicao.
        double repeticaoEsperada = elegiveis.stream()
            .mapToDouble(aluno -> {
                var turma = turmaVigente(aluno, dia, vinculos);
                return turma == null ? 0 : taxaDeRepeticao(turma.getCodigo());
            }).average().orElse(0);
        double efeitoDoCardapio = 1 - PESO_DO_CARDAPIO * (1 - catalogo.fatorDeAdesao(nomesDosItens(cardapio)));
        double adesao = (metaDeAtendimento(refeicao.getId()) + variacao) / (1 + repeticaoEsperada)
            * efeitoDoCardapio;
        int autorizados = (int) Math.round(
            cardapio.getQuantidadePlanejada() * Math.min(0.95, Math.max(0.05, adesao)));
        autorizados = Math.min(autorizados, elegiveis.size());
        int bloqueados = (int) Math.round(autorizados * PROPORCAO_BLOQUEIOS);

        List<Aluno> embaralhados = new ArrayList<>(elegiveis);
        java.util.Collections.shuffle(embaralhados, rng);

        List<AuditoriaConsumo> registros = new ArrayList<>(autorizados + bloqueados);
        List<AuditoriaConsumo> primeiras = new ArrayList<>(autorizados);
        for (int indice = 0; indice < autorizados; indice++) {
            Aluno aluno = embaralhados.get(indice);
            var primeira = criar(aluno, cardapio, dia, refeicao, vinculos, rng, ResultadoConsumo.AUTORIZADO, null,
                chave(aluno, dia, refeicao), primeiraLeitura(dia, refeicao, rng));
            primeiras.add(primeira);
            registros.add(primeira);
        }

        // Repeticao: mesma via de criar(), entao a turma carimbada e a mesma da primeira. A chave
        // unica fica so na primeira; a fila real tambem grava chave nula nas autorizacoes seguintes.
        for (int indice = 0; indice < autorizados; indice++) {
            var primeira = primeiras.get(indice);
            if (rng.nextDouble() >= taxaDeRepeticao(primeira.getTurmaNome())) continue;
            Instant depoisDaJanela = primeira.getInstante().plus(Duration.ofMinutes(3 + rng.nextInt(15)));
            registros.add(criar(embaralhados.get(indice), cardapio, dia, refeicao, vinculos, rng,
                ResultadoConsumo.AUTORIZADO, null, null, depoisDaJanela));
        }

        // Bloqueios reusam alunos que ja consumiram — o motivo mais comum na fila e justamente
        // a segunda tentativa na mesma refeicao. Chave nula porque a unica cabe so ao autorizado.
        for (int indice = 0; indice < bloqueados && indice < autorizados; indice++) {
            Aluno aluno = embaralhados.get(indice);
            registros.add(criar(aluno, cardapio, dia, refeicao, vinculos, rng, ResultadoConsumo.BLOQUEADO,
                MOTIVOS_BLOQUEIO[rng.nextInt(MOTIVOS_BLOQUEIO.length)], null, primeiraLeitura(dia, refeicao, rng)));
        }

        // Instante futuro nao existe: apresentar as 10h com consumo "da tarde" ja gravado seria
        // incoerente. O que ficou de fora nao e recuperado: a refeicao que ja tem AUTORIZADO e
        // pulada nas cargas seguintes, entao o restante dela fica sem consumo sintetico.
        registros.removeIf(registro -> registro.getInstante().isAfter(agora));
        auditorias.saveAll(registros);
        return registros.size();
    }

    private AuditoriaConsumo criar(Aluno aluno, Cardapio cardapio, LocalDate dia, TipoRefeicao refeicao,
                                   Map<UUID, List<Enturmacao>> vinculos, Random rng,
                                   ResultadoConsumo resultado, String motivo, String chave,
                                   Instant instante) {
        Turma vigente = turmaVigente(aluno, dia, vinculos);
        return new AuditoriaConsumo(
            UUID.randomUUID(),
            aluno,
            cardapio,
            dia,
            refeicao.getId(),
            refeicao.getNome(),
            instante,
            rng.nextInt(10) == 0 ? MetodoIdentificacao.FACIAL : MetodoIdentificacao.QR_CODE,
            resultado,
            motivo,
            UUID.randomUUID(),
            chave,
            vigente == null ? null : vigente.getId(),
            vigente == null ? AuditoriaConsumo.SEM_TURMA : vigente.getCodigo()
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

    private String chave(Aluno aluno, LocalDate dia, TipoRefeicao refeicao) {
        return aluno.getId() + ":" + dia + ":" + refeicao.getId();
    }

    /**
     * Os 20 minutos finais da janela ficam livres para a repeticao, que cai de 3 a 17 minutos
     * depois da primeira leitura e precisa continuar dentro da refeicao.
     */
    private Instant primeiraLeitura(LocalDate dia, TipoRefeicao refeicao, Random rng) {
        long minutosDaJanela = Duration.between(refeicao.getInicio(), refeicao.getFim()).toMinutes();
        LocalTime horario = refeicao.getInicio().plusMinutes(rng.nextInt((int) Math.max(1, minutosDaJanela - 20)));
        return dia.atTime(horario).atZone(ApplicationConfig.ZONA_OPERACIONAL).toInstant();
    }
}
