# Refeições por turno — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a refeição (café, lanches, almoço) vira a unidade do cardápio, da fila, do fechamento e da análise; o regime vem do turno da turma (`INTEGRAL` = integral, demais = parcial).

**Architecture:** grade de refeições semeada por migration (`tipo_refeicao` + turnos com direito) e resolvida por horário + turno da turma num `RefeicaoResolver` que substitui o `TurnoResolver`. As tabelas ganham `tipo_refeicao_id` em expand (V15, com backfill pelo mapeamento do turno) e perdem `turno` em contract (V16), exceto `relatorio_ia.turno`, que fica para os relatórios antigos. O contrato Java → Python troca `turno` por `refeicao` (`schemaVersion` 5), com o Python aceitando os dois até o contract.

**Tech Stack:** Spring Boot 3.5 / Java 25 / JPA / Flyway / H2 / Mockito / MockMvc; FastAPI + Pydantic; Angular 20 (signals, Karma + Jasmine), Tailwind 4.

**Spec:** `api-merenda-escolar/docs/superpowers/specs/2026-11-09-refeicoes-por-turno-design.md`

Caminhos relativos a `/home/frank.serrano/Documentos/projeto ia/`. Pacote Java `br.com.frankcardoso.merenda` aparece como `.../merenda/` (raiz `api-merenda-escolar/src/main/java/br/com/frankcardoso`; testes no espelho em `src/test/java`).

## Global Constraints

- **Sem commit.** Nenhum `git add`/`commit`/`push`. Cada task termina mostrando `git diff --stat` dos repositórios tocados. O Frank commita quando pedir e é perguntado sobre o ticket a cada commit. Plano e spec não vão para o git.
- **Maven:** em `api-merenda-escolar`, `JAVA_HOME=$HOME/.jdks/corretto-25.0.4.1 $HOME/.m2/wrapper/dists/apache-maven-3.9.16-bin/5grr65jo27hi51sujmtcldfovl/apache-maven-3.9.16/bin/mvn -B -q test` (abaixo: `$MVN test`; uma classe: `$MVN test -Dtest=Classe -Dsurefire.failIfNoSpecifiedTests=false`). O `mvn` do PATH é 3.5.4/Java 8 e não serve.
- **A API pode estar rodando no IntelliJ do Frank** (porta 8081, a partir de `target/classes`). Nunca encerrar nem reiniciar esse processo. Os testes usam H2 em memória e não tocam `data/`. Se ele reiniciar no meio do plano, as migrations novas rodam na base dele, o que é seguro; a base é recriada na Task 16.
- **Front:** em `front-merenda-escolar`, `PATH=$HOME/.nvm/versions/node/v20.19.0/bin:$PATH CHROME_BIN=/tmp/claude-1975829934/-home-frank-serrano/dd761c4d-f72d-487e-b365-b552c902be76/scratchpad/chrome-ns.sh pnpm test --watch=false --browsers=ChromeHeadless` e `PATH=$HOME/.nvm/versions/node/v20.19.0/bin:$PATH npx tsc -p tsconfig.app.json --noEmit`.
- **Python:** em `analytics-merenda-escolar`, `./.venv/bin/python -m pytest -q`. Ruff só nos arquivos tocados; há 5 erros antigos em `indicadores_logisticos.py` que não se mexe.
- **Cada task deixa as três suítes verdes.** Banco e contrato Java → Python em expand-then-contract (V15 expand, V16 e Task 12 contract). `relatorio_ia.turno` e o `turno` da fotografia `schemaVersion` 4 continuam legíveis.
- **A fila nunca devolve 500:** toda recusa é `200` com `resultado=BLOQUEADO` e `motivo`. Motivos novos: `FORA_DO_HORARIO_DE_REFEICAO`, `REFEICAO_NAO_PREVISTA_PARA_TURMA`, `GRADE_DE_REFEICOES_INVALIDA`. `FORA_DO_TURNO` deixa de existir.
- **Grade inicial (fixa, ids fixos; enum `RefeicaoPadrao`):**

  | Enum | id | nome | ordem | janela | turnos com direito |
  |---|---|---|---|---|---|
  | `CAFE_DA_MANHA` | `5e1a0000-0000-4000-8000-000000000001` | Café da manhã | 1 | 07:00–08:00 | INTEGRAL |
  | `LANCHE_DA_MANHA` | `5e1a0000-0000-4000-8000-000000000002` | Lanche da manhã | 2 | 09:30–10:30 | MANHA, INTEGRAL |
  | `ALMOCO` | `5e1a0000-0000-4000-8000-000000000003` | Almoço | 3 | 11:30–13:00 | INTEGRAL |
  | `LANCHE_DA_TARDE` | `5e1a0000-0000-4000-8000-000000000004` | Lanche da tarde | 4 | 15:00–16:00 | TARDE, INTEGRAL |
  | `LANCHE_DA_NOITE` | `5e1a0000-0000-4000-8000-000000000005` | Lanche da noite | 5 | 20:00–21:00 | NOITE |

  Janela é `[início, fim)`, hora local de `America/Sao_Paulo` (`ApplicationConfig.ZONA_OPERACIONAL`).
- **Mapeamento do legado:** turno → refeição: `MANHA→LANCHE_DA_MANHA`, `TARDE→LANCHE_DA_TARDE`, `NOITE→LANCHE_DA_NOITE`, `INTEGRAL→ALMOCO`. Nome do legado (`historico_consumo.refeicao`) → refeição, por prefixo: `Cafe d%` → café da manhã; `Lanche da manha%` → lanche da manhã; `Almoco%` → almoço; `Lanche da tarde%` → lanche da tarde; o resto (`Frutas - Lanches`, `Lanche do turno estendido - EMEIS`, `Lanches manha e tarde - Jardim EMEIS`, `Lanches manha ou tarde - EMEFS parcial`) fica `NULL` e fora da análise por refeição.
- `tipoRefeicaoId` desconhecido em parâmetro ou corpo: `TipoRefeicaoNaoEncontradoException` → 404 com `"Refeição <id> não existe na grade."`.
- Testes só com dados inventados (alunos, turmas, códigos `ALU-T…`). Textos de tela e mensagens novas em pt-BR com acento. Comentário só para o que o código não diz, sem número de ticket.
- Contrato REST API ↔ front muda sem expand: as suítes do front mockam HTTP e ficam verdes; a tela em execução fica fora de sincronia entre a Task 5 e a Task 14 (só importa se alguém usar a tela no meio do plano).

## Review Focus

- **Leitura exatamente no limite da janela** (08:00, 13:00): fim exclusivo, então 08:00 é `FORA_DO_HORARIO_DE_REFEICAO`; 07:00 e 11:30 já valem. Teste `limitesDaJanela` na Task 1.
- **Grade apagada ou com sobreposição no banco:** a fila responde `BLOQUEADO`/`GRADE_DE_REFEICOES_INVALIDA` com 200, nunca 500. Teste `gradeInvalidaBloqueiaSemErro` na Task 6.
- **Aluno sem turma vigente:** vale a refeição cuja janela contém o horário (menor `ordem` se houver mais de uma), e a auditoria carimba "Sem turma". Testes na Task 1 (`semTurma…`) e Task 6 (`alunoSemTurmaUsaARefeicaoDaJanela`).
- **Relatório gerado antes da mudança** (`turno=MANHA`, fotografia `schemaVersion` 4): abre pelo id e aparece na listagem por `(data, Lanche da manhã)`. Teste `relatorioAntigoComTurnoContinuaLegivelEListado` na Task 10.
- **`tipoRefeicaoId` que não existe** (front com lista antiga, chamada direta): 404 com mensagem, nunca 500. Testes na Task 5 (`recusaRefeicaoForaDaGrade`) e Task 10 (`refeicaoForaDaGradeRetorna404`).

---

### Task 1: API — grade de refeições

**Files:**
- Create: `api-merenda-escolar/src/main/resources/db/migration/V14__grade_de_refeicoes.sql`
- Create: `.../merenda/refeicao/domain/TipoRefeicao.java`, `RefeicaoPadrao.java`, `GradeRefeicoes.java`, `ResolucaoRefeicao.java`, `GradeRefeicoesInvalidaException.java`
- Create: `.../merenda/refeicao/infrastructure/TipoRefeicaoRepository.java`
- Create: `.../merenda/refeicao/application/GradeRefeicoesService.java`, `TipoRefeicaoNaoEncontradoException.java`
- Create: `.../merenda/refeicao/api/TipoRefeicaoController.java`, `TipoRefeicaoResponse.java`
- Modify: `.../merenda/shared/web/RestExceptionHandler.java:25-32` (404 para `TipoRefeicaoNaoEncontradoException`)
- Test: `.../merenda/refeicao/domain/GradeRefeicoesTest.java`, `.../merenda/refeicao/api/GradeSemeadaHttpTest.java`

**Interfaces:**
- Produces:
  - V14: `tipo_refeicao(id UUID PK, nome VARCHAR(60) NOT NULL UNIQUE, ordem INTEGER NOT NULL UNIQUE, inicio TIME NOT NULL, fim TIME NOT NULL, CHECK (inicio < fim))` e `tipo_refeicao_turno(tipo_refeicao_id UUID NOT NULL FK, turno VARCHAR(20) NOT NULL, PK(tipo_refeicao_id, turno))`, com os 5 registros da tabela das Global Constraints.
  - `@Entity TipoRefeicao(UUID id, String nome, int ordem, LocalTime inicio, LocalTime fim, Set<Turno> turnos)`; `turnos` é `@ElementCollection(fetch = EAGER)` em `tipo_refeicao_turno`, `@Enumerated(STRING)`. Métodos `boolean contem(LocalTime)`, `boolean atende(Turno)`, `boolean sobrepoe(TipoRefeicao)`, getters.
  - `enum RefeicaoPadrao { CAFE_DA_MANHA, LANCHE_DA_MANHA, ALMOCO, LANCHE_DA_TARDE, LANCHE_DA_NOITE }` com `UUID id()`, `String nome()`. Os mapeamentos do legado entram na Task 3.
  - `record ResolucaoRefeicao(TipoRefeicao refeicao, String motivoBloqueio)` com `static autorizada(TipoRefeicao)`, `static bloqueada(String)`, `boolean bloqueada()`.
  - `final class GradeRefeicoes`: `static GradeRefeicoes de(List<TipoRefeicao>)` lança `GradeRefeicoesInvalidaException` se vazia ou se duas refeições que atendem o mesmo turno se sobrepõem; `Optional<String> bloqueioDoHorario(LocalTime)` (vazio se alguma janela contém o horário, senão `FORA_DO_HORARIO_DE_REFEICAO`); `ResolucaoRefeicao resolver(LocalTime horario, Turno turnoDaTurma)` (`turnoDaTurma` nulo = aluno sem turma).
  - `TipoRefeicaoRepository extends JpaRepository<TipoRefeicao, UUID>`: `List<TipoRefeicao> findAllByOrderByOrdemAsc()`.
  - `@Service GradeRefeicoesService`: `List<TipoRefeicao> listar()`, `TipoRefeicao buscar(UUID id)` (lança `TipoRefeicaoNaoEncontradoException(UUID)`), `GradeRefeicoes grade()`.
  - `GET /api/v1/tipos-refeicao` → `List<TipoRefeicaoResponse(UUID id, String nome, int ordem, LocalTime inicio, LocalTime fim, List<Turno> turnos)>` em ordem, turnos na ordem do enum.

Regra de `resolver` (a assinatura não determina): entre as refeições cuja janela contém o horário, com turma vale a única que `atende(turno)`; se há janela mas nenhuma atende, `REFEICAO_NAO_PREVISTA_PARA_TURMA`; sem turma vale a de menor `ordem`; sem janela, `FORA_DO_HORARIO_DE_REFEICAO`.

- [ ] **Step 1: Escrever os testes que falham.** `GradeRefeicoesTest` monta a grade padrão em memória (sem Spring):
  - `cadaJanelaResolveSuaRefeicao`: `(07:00, INTEGRAL)`→Café da manhã; `(07:59, INTEGRAL)`→Café da manhã; `(09:30, MANHA)`→Lanche da manhã; `(10:00, INTEGRAL)`→Lanche da manhã; `(12:00, INTEGRAL)`→Almoço; `(15:30, TARDE)`→Lanche da tarde; `(20:30, NOITE)`→Lanche da noite.
  - `limitesDaJanela`: `(08:00, INTEGRAL)` e `(13:00, INTEGRAL)` → `motivoBloqueio == "FORA_DO_HORARIO_DE_REFEICAO"`; `(11:30, INTEGRAL)` → Almoço.
  - `foraDeQualquerJanela`: `bloqueioDoHorario(14:00)` → `Optional.of("FORA_DO_HORARIO_DE_REFEICAO")`; `bloqueioDoHorario(12:00)` vazio.
  - `semDireitoNaJanela`: `(12:00, MANHA)` e `(07:30, TARDE)` → `REFEICAO_NAO_PREVISTA_PARA_TURMA`.
  - `semTurmaValeAJanelaDoHorario`: `(12:00, null)` → Almoço.
  - `semTurmaComJanelasSobrepostasDeTurnosDiferentesValeMenorOrdem`: grade A (ordem 1, 07:00–08:00, {MANHA}) e B (ordem 2, 07:30–08:30, {TARDE}); `(07:45, null)` → A; `(07:45, TARDE)` → B.
  - `sobreposicaoNoMesmoTurnoEhRecusada`: A {INTEGRAL} 07:00–08:00 e B {INTEGRAL} 07:30–09:00 → `GradeRefeicoesInvalidaException`.
  - `gradeVaziaEhInvalida`.
  - `GradeSemeadaHttpTest` (`@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:gradesemeada;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`):
    - `gradeSemeadaEhValidaEUsaOsIdsDoEnum`: `service.grade()` não lança; ids de `listar()` == `RefeicaoPadrao.values()` na ordem, nomes iguais.
    - `listaAGradeEmOrdem`: `GET /api/v1/tipos-refeicao` → `$.length()==5`, `$[2].nome=="Almoço"`, `$[2].inicio=="11:30:00"`, `$[2].fim=="13:00:00"`, `$[2].turnos==["INTEGRAL"]`, `$[1].turnos==["MANHA","INTEGRAL"]`.
- [ ] **Step 2:** `$MVN test -Dtest='GradeRefeicoesTest,GradeSemeadaHttpTest' -Dsurefire.failIfNoSpecifiedTests=false` → falha de compilação.
- [ ] **Step 3: Implementar** os arquivos acima. Na migration, os ids entram com `CAST('…' AS UUID)`.
- [ ] **Step 4:** mesmo comando → PASS; depois `$MVN test` → BUILD SUCCESS.
- [ ] **Step 5:** `git -C api-merenda-escolar diff --stat`.

---

### Task 2: API — relógio de demonstração `merenda.demo.horario`

**Files:**
- Modify: `.../merenda/shared/config/ApplicationConfig.java`
- Test: `.../merenda/shared/config/ApplicationConfigTest.java`

**Interfaces:**
- Produces: `static Clock relogio(Clock real, String horarioDemonstracao)` em `ApplicationConfig`; bean `Clock clock(@Value("${merenda.demo.horario:}") String horario)` = `relogio(Clock.systemUTC(), horario)`.

Comportamento: vazio/nulo devolve `real`. Com `HH:mm`, devolve `Clock.offset(real, delta)`, com `delta` calculado uma vez entre `real.instant()` e `LocalDate.now(real.withZone(ZONA_OPERACIONAL)).atTime(horario)` na zona operacional. O relógio começa no horário e avança, o que a fila precisa para os 2 minutos de repetição. Valor inválido lança `IllegalArgumentException` com `"merenda.demo.horario"` na mensagem e a subida falha. Log INFO `Relógio de demonstração: hoje às HH:mm`.

- [ ] **Step 1: Testes que falham** (`ApplicationConfigTest`, relógio real controlável por uma subclasse de `Clock` com `AtomicReference<Instant>`):
  - `semHorarioUsaORelogioReal`: `relogio(real, "")` e `relogio(real, null)` → mesmo `instant()` que `real`.
  - `comHorarioComecaNaDataRealNoHorarioInformado`: real em `2026-11-09T16:37:00Z`, `"12:00"` → `2026-11-09T15:00:00Z`.
  - `usaADataDaZonaOperacional`: real em `2026-11-10T01:30:00Z` (22:30 de 09/11 em São Paulo), `"07:10"` → `2026-11-09T10:10:00Z`.
  - `relogioDeDemonstracaoAvancaComORelogioReal`: depois de avançar o real 3 minutos, o de demonstração está em `15:03:00Z`.
  - `horarioInvalidoFalhaNaSubida`: `"25:00"` → `IllegalArgumentException` com mensagem contendo `merenda.demo.horario`.
- [ ] **Step 2:** `$MVN test -Dtest=ApplicationConfigTest -Dsurefire.failIfNoSpecifiedTests=false` → FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** a classe passa e `$MVN test` dá BUILD SUCCESS.
- [ ] **Step 5:** `git -C api-merenda-escolar diff --stat`.

---

### Task 3: API — expand: `tipo_refeicao_id` nas tabelas, com backfill

**Files:**
- Create: `api-merenda-escolar/src/main/resources/db/migration/V15__refeicao_nas_tabelas.sql`
- Modify: `.../merenda/refeicao/domain/RefeicaoPadrao.java` (mapeamentos)
- Modify: `.../merenda/cardapio/domain/Cardapio.java`, `.../merenda/fila/domain/AuditoriaConsumo.java`, `.../merenda/medicao/domain/MedicaoSobra.java`, `.../merenda/relatorio/domain/RelatorioIA.java`, `.../merenda/historico/domain/HistoricoConsumo.java`
- Test: `.../merenda/refeicao/domain/RefeicaoPadraoTest.java`, `.../merenda/refeicao/infrastructure/MigracaoRefeicoesTest.java`, `.../merenda/refeicao/domain/TurnoLegadoNasEntidadesTest.java` (sai na Task 12)

**Interfaces:**
- Consumes: `RefeicaoPadrao` (Task 1).
- Produces:
  - `static RefeicaoPadrao doTurnoLegado(Turno)` e `static Optional<RefeicaoPadrao> doNomeLegado(String)`, com as regras das Global Constraints.
  - V15, nesta ordem: `ADD COLUMN tipo_refeicao_id UUID` em `cardapio`, `auditoria_consumo`, `medicao_sobra`, `relatorio_ia`, `historico_consumo`, mais `auditoria_consumo.refeicao_nome VARCHAR(60)`. Backfill com `CASE turno` (as quatro primeiras) e `CASE` por prefixo de `refeicao` (histórico). Depois: `SET NOT NULL` em `cardapio`, `auditoria_consumo` e `medicao_sobra`; `turno` passa a aceitar nulo nessas três e em `historico_consumo` (`ALTER COLUMN turno SET NULL`); FKs para `tipo_refeicao`; índices `idx_cardapio_data_refeicao_ativo(data, tipo_refeicao_id, ativo)`, `idx_auditoria_data_refeicao(data_operacional, tipo_refeicao_id)`, `idx_historico_data_refeicao(data, tipo_refeicao_id)`; `uk_medicao_sobra_refeicao UNIQUE(data, tipo_refeicao_id, escola_id, receita_id)`.
  - Entidades: campo `UUID tipoRefeicaoId` (`@Column(name = "tipo_refeicao_id")`) e getter nas cinco; `AuditoriaConsumo` também `String refeicaoNome` e getter. O campo `turno` passa a nullable no mapeamento.
  - Construtores novos, sem turno e com `UUID tipoRefeicaoId` no lugar dele: `Cardapio(UUID, LocalDate, UUID tipoRefeicaoId, String nomeRefeicao, String descricao, String itensJson, int quantidadePlanejada, boolean ativo)`; `Cardapio.atualizar(LocalDate, UUID tipoRefeicaoId, String, String, String, int)`; `AuditoriaConsumo(UUID, Aluno, Cardapio, LocalDate, UUID tipoRefeicaoId, String refeicaoNome, Instant, MetodoIdentificacao, ResultadoConsumo, String motivo, UUID correlationId, String chave, UUID turmaId, String turmaNome)`; `MedicaoSobra(UUID, LocalDate, UUID tipoRefeicaoId, int escolaId, UUID receitaId, int, int, int, int, String origem, Instant)`; `HistoricoConsumo(UUID, LocalDate, UUID tipoRefeicaoId, int escolaId, String refeicao, UUID receitaId, int, Integer, String)`.
  - Os construtores e `atualizar` com `Turno` continuam até a Task 12 e preenchem `tipoRefeicaoId` (e `refeicaoNome`) por `doTurnoLegado`. O `HistoricoConsumo` com `Turno` usa `doNomeLegado(refeicao)`. `RelatorioIA.pendente(...)` com `Turno` também preenche `tipoRefeicaoId`.

- [ ] **Step 1: Testes que falham.**
  - `RefeicaoPadraoTest.turnoLegadoMapeiaParaARefeicao`: os 4 pares das Global Constraints.
  - `RefeicaoPadraoTest.nomesDoLegadoMapeiamComoAMigration`: `Cafe de manha - Turnos Integrais`, `Cafe da manha EMEIS ou menores de 1 ano` → `CAFE_DA_MANHA`; `Almoco - EMEIS` → `ALMOCO`; `Lanche da manha - Jardim EMEIS` → `LANCHE_DA_MANHA`; `Lanche da tarde, menores de um ano - EMEIS` → `LANCHE_DA_TARDE`; `Lanches manha ou tarde - EMEFS parcial`, `Lanche do turno estendido - EMEIS`, `Frutas - Lanches`, `Lanches manha e tarde - Jardim EMEIS` → vazio.
  - `MigracaoRefeicoesTest.dadosAntigosRecebemARefeicaoMapeada` (sem Spring): Flyway em `jdbc:h2:mem:migracao-refeicoes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`, `target("14")`. Insere por JDBC uma receita, cardápios `INTEGRAL` e `MANHA`, uma auditoria `TARDE`, uma medição `MANHA`, relatórios `NOITE` e com `turno` nulo, e uma linha de histórico para cada nome do caso acima. Roda `migrate()` até o fim e confere: cardápios → `ALMOCO`/`LANCHE_DA_MANHA`; auditoria → `LANCHE_DA_TARDE` com `refeicao_nome = 'Lanche da tarde'`; medição → `LANCHE_DA_MANHA`; relatório `NOITE` → `LANCHE_DA_NOITE` e o nulo continua nulo; histórico igual a `doNomeLegado` linha a linha. Só asserções sobre `tipo_refeicao_id`/`refeicao_nome`, para o teste sobreviver à V16.
  - `TurnoLegadoNasEntidadesTest.construtorComTurnoPreencheARefeicao`: `new Cardapio(..., Turno.INTEGRAL, ...)` → `ALMOCO.id()`; `AuditoriaConsumo` com `Turno.MANHA` → id e nome de Lanche da manhã; `MedicaoSobra` `TARDE` → `LANCHE_DA_TARDE`; `RelatorioIA.pendente(data, Turno.NOITE, agora)` → `LANCHE_DA_NOITE`; `HistoricoConsumo` com `"Almoco - EMEIS"` → `ALMOCO` e com `"Frutas - Lanches"` → `null`.
- [ ] **Step 2:** rodar as três classes → FAIL.
- [ ] **Step 3:** implementar migration, mapeamentos e entidades.
- [ ] **Step 4:** as três classes passam; `$MVN test` dá BUILD SUCCESS (o `ddl-auto: validate` confere as colunas novas).
- [ ] **Step 5:** `git -C api-merenda-escolar diff --stat`.

---

### Task 4: Python — expand do contrato: `refeicao` e `schemaVersion` 5

**Files:**
- Modify: `analytics-merenda-escolar/app/domain/indicadores_logisticos.py`, `analytics-merenda-escolar/app/domain/previsao_consumo.py`
- Test: `analytics-merenda-escolar/tests/domain/test_indicadores_logisticos.py`, `tests/api/test_indicadores_controller.py`, `tests/domain/test_previsao_consumo.py`, `tests/api/test_previsoes_controller.py`

**Interfaces:**
- Produces:
  - `class RefeicaoRef(Contrato)`: `id: str`, `nome: str`.
  - `IndicadoresRequest`: `turno: Literal["MANHA","TARDE","NOITE","INTEGRAL"] | None = None`, `refeicao: RefeicaoRef | None = None`, `historico_sem_refeicao: int = Field(default=0, ge=0)`. O validador recusa quando os dois vêm nulos, com `"Informe a refeição"`.
  - `IndicadoresResponse`: `schema_version = "5"`, `turno: str | None = None`, `refeicao: RefeicaoRef | None = None` (eco do pedido).
  - Avisos: `"Sem medição de sobra lançada para este dia e refeição: aceitação e desperdício ficam indisponíveis até alguém registrar."`; com `historico_sem_refeicao > 0`, `f"{n} registros do histórico do legado não têm refeição correspondente (lanches mistos, turno estendido) e ficaram fora da análise por refeição."`.
  - `PrevisaoConsumoRequest`: `turno: str | None = None`, `refeicao: str | None = None`.

- [ ] **Step 1: Testes que falham.**
  - `test_indicadores_ecoam_a_refeicao_no_schema_5`: `_request` com `refeicao={"id": "5e1a0000-0000-4000-8000-000000000003", "nome": "Almoço"}` e sem turno → `schema_version == "5"`, `refeicao.nome == "Almoço"`, `turno is None`.
  - `test_aviso_de_historico_sem_refeicao`: `historico_sem_refeicao=12` → algum aviso começa com `"12 registros do histórico do legado"`; com `0`, nenhum aviso cita o legado.
  - `test_sem_refeicao_nem_turno_e_recusado` (controller): payload sem os dois → 422.
  - `test_previsao_aceita_refeicao_no_lugar_do_turno` (controller): `"refeicao": "Almoço"` sem `turno` → 200.
  - Testes existentes com `turno` continuam passando. Os que leem `schemaVersion` passam a esperar `"5"`.
- [ ] **Step 2:** `./.venv/bin/python -m pytest -q` → FAIL nos novos.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** pytest verde; ruff nos dois arquivos de `app/` sem erro novo.
- [ ] **Step 5:** `git -C analytics-merenda-escolar diff --stat`.

---

### Task 5: API — cardápio por (data, refeição)

**Files:**
- Modify: `.../merenda/cardapio/api/CardapioRequest.java`, `CardapioResponse.java`; `.../cardapio/application/CardapioService.java`, `CardapioDuplicadoException.java`; `.../cardapio/infrastructure/CardapioRepository.java`
- Test: `.../merenda/cardapio/application/CardapioServiceTest.java`

**Interfaces:**
- Consumes: `GradeRefeicoesService.buscar/listar` (Task 1), construtor e `atualizar` novos de `Cardapio` (Task 3).
- Produces:
  - `CardapioRequest(@NotNull LocalDate data, @NotNull UUID tipoRefeicaoId, nomeRefeicao, descricao, itens, quantidadePlanejada)`, sem `turno`.
  - `CardapioResponse(UUID id, LocalDate data, UUID tipoRefeicaoId, String refeicao, String nomeRefeicao, String descricao, List<ItemCardapioDto> itens, int quantidadePlanejada, boolean ativo)`.
  - `CardapioRepository` ganha `Optional<Cardapio> findByDataAndTipoRefeicaoIdAndAtivoTrue(LocalDate, UUID)`, `boolean existsByDataAndTipoRefeicaoIdAndAtivoTrue(LocalDate, UUID)`, `List<Cardapio> findAllByAtivoTrueOrderByDataDesc()`, `List<Cardapio> findAllByDataBetweenAndTipoRefeicaoIdAndAtivoTrueOrderByDataAsc(LocalDate, LocalDate, UUID)`. Os métodos com `Turno` ficam até a Task 12.
  - `CardapioDuplicadoException(LocalDate data, String refeicao)`: `"Já existe um cardápio ativo para %s em %s."`.
  - `CardapioService(CardapioRepository, ReceitaRepository, GradeRefeicoesService, ObjectMapper, Clock)`. `listar()` ordena por data decrescente e depois pela `ordem` da refeição.

- [ ] **Step 1: Atualizar e escrever os testes** (mocks, refeições `ALMOCO` e `LANCHE_DA_MANHA` montadas como `TipoRefeicao`):
  - `deveCriarCardapioDaRefeicao`: salvo com `tipoRefeicaoId == ALMOCO.id()`, `turno` nulo, resposta com `refeicao == "Almoço"`.
  - `naoDeveCriarSegundoCardapioAtivoParaMesmaDataERefeicao`: `existsByDataAndTipoRefeicaoIdAndAtivoTrue` true → `CardapioDuplicadoException` com mensagem `"Já existe um cardápio ativo para <HOJE> em Almoço."`.
  - `permiteCardapiosDeRefeicoesDiferentesNoMesmoDia`: o Almoço existe e o Lanche da manhã é criado.
  - `recusaRefeicaoForaDaGrade`: `buscar` lança `TipoRefeicaoNaoEncontradoException` e nada é salvo.
  - `listaPorDataDecrescenteENaOrdemDaGrade`: mesmo dia, Almoço e Café → Café primeiro.
  - Os testes de data passada, desativação, atualização e receita desconhecida passam a usar refeição em vez de turno.
- [ ] **Step 2:** `$MVN test -Dtest=CardapioServiceTest -Dsurefire.failIfNoSpecifiedTests=false` → FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** a classe passa; `$MVN test` dá BUILD SUCCESS.
- [ ] **Step 5:** `git -C api-merenda-escolar diff --stat`.

---

### Task 6: API — fila resolve a refeição pelo horário e pelo turno da turma

**Files:**
- Create: `.../merenda/fila/application/RefeicaoResolver.java`
- Delete: `.../merenda/fila/application/TurnoResolver.java`
- Modify: `.../merenda/fila/application/ValidacaoConsumoService.java`, `.../fila/api/ValidarConsumoResponse.java`, `.../fila/infrastructure/AuditoriaConsumoRepository.java`
- Test: `.../merenda/fila/application/ValidacaoConsumoServiceTest.java` (reescrever o setup), `.../merenda/fila/application/FilaPorRefeicaoIntegracaoTest.java`

**Interfaces:**
- Consumes: `GradeRefeicoesService.grade()`, `GradeRefeicoes`, `ResolucaoRefeicao` (Task 1); construtor novo de `AuditoriaConsumo` (Task 3); `CardapioRepository.findByDataAndTipoRefeicaoIdAndAtivoTrue` (Task 5); `EnturmacaoRepository.vigenteEm`.
- Produces:
  - `@Component RefeicaoResolver(GradeRefeicoesService)`: `Optional<String> bloqueioDoHorario(LocalTime)` e `ResolucaoRefeicao resolver(LocalTime, Turno turnoDaTurma)`. `GradeRefeicoesInvalidaException` vira `GRADE_DE_REFEICOES_INVALIDA`, com log WARN.
  - `ValidarConsumoResponse(ResultadoConsumo resultado, String sinal, UUID alunoId, String alunoNome, UUID tipoRefeicaoId, String refeicao, String cardapio, Instant registradoEm, String motivo)` com `static bloqueado(String motivo)`.
  - `AuditoriaConsumoRepository.existsByAlunoIdAndDataOperacionalAndTipoRefeicaoIdAndResultadoAndInstanteGreaterThanEqual(UUID, LocalDate, UUID, ResultadoConsumo, Instant)`, que substitui o método por turno.
  - Construtor: `ValidacaoConsumoService(AlunoRepository, CardapioRepository, AuditoriaConsumoRepository, EnturmacaoRepository, RefeicaoResolver, IdentificacaoFacialService, TransactionTemplate, Clock, Duration bloqueioRepeticao)`.

Fluxo:
1. `bloqueioDoHorario` antes de identificar: se houver bloqueio, `bloqueado(motivo)` sem auditoria nem chamada facial.
2. Identificação facial, como hoje.
3. Na transação: aluno (`ALUNO_NAO_ENCONTRADO_OU_INATIVO`).
4. Turma vigente, `resolver(horario, turma?.getTurno())`. Bloqueio aqui responde com aluno e sem auditoria.
5. Cardápio `(data, refeição)` (`CARDAPIO_NAO_CONFIGURADO`).
6. Repetição por `(aluno, data, refeição)` dentro de `bloqueioRepeticao`: audita BLOQUEADO.
7. Audita AUTORIZADO com `tipoRefeicaoId` e `refeicaoNome`. A turma vigente é buscada uma vez e reaproveitada no carimbo.

- [ ] **Step 1: Testes que falham** (`ValidacaoConsumoServiceTest`: `RefeicaoResolver` real sobre um `GradeRefeicoesService` mockado com a grade padrão; `Clock.fixed` em `ZONA_OPERACIONAL`):
  - `integralAutorizadoNoCafeENoAlmocoDoMesmoDia`: turma `INTEGRAL`; às 07:10 sai AUTORIZADO com `refeicao == "Café da manhã"`; com outro serviço às 12:00, AUTORIZADO `"Almoço"`. O `exists…` é consultado com `CAFE_DA_MANHA.id()` e depois com `ALMOCO.id()`.
  - `parcialDaManhaBarradoNoAlmoco`: turma `MANHA`, 12:00 → BLOQUEADO `REFEICAO_NAO_PREVISTA_PARA_TURMA`, `alunoNome` preenchido, `auditoriaRepository` sem `save*`.
  - `foraDeQualquerJanelaBloqueiaAntesDeIdentificar`: 14:00 → `FORA_DO_HORARIO_DE_REFEICAO`; sem interação com `alunoRepository` e `identificacaoFacial`.
  - `repeticaoNaMesmaRefeicaoDentroDaJanelaBloqueia`: `exists…` true → `CONSUMO_RECENTE_BLOQUEADO`, auditoria BLOQUEADO com `tipoRefeicaoId == ALMOCO.id()`.
  - `repeticaoNaMesmaRefeicaoDepoisDaJanelaAutoriza`: `exists…` chamado com `instante == agora.minus(2 min)` e retorno false → AUTORIZADO.
  - `alunoSemTurmaUsaARefeicaoDaJanela`: sem enturmação, 09:45 → AUTORIZADO "Lanche da manhã", auditoria com `turmaNome == "Sem turma"`.
  - `gradeInvalidaBloqueiaSemErro`: `grade()` lança → BLOQUEADO `GRADE_DE_REFEICOES_INVALIDA`, sem exceção.
  - `cardapioDaRefeicaoAusenteBloqueia`: `CARDAPIO_NAO_CONFIGURADO`.
  - `auditoriaGravaIdENomeDaRefeicao`: o captor confere `getTipoRefeicaoId()` e `getRefeicaoNome()`.
  - Testes facial, de lock e de carimbo de turma adaptados ao novo setup (12:00, Almoço, turma `INTEGRAL`).
  - `FilaPorRefeicaoIntegracaoTest` (`@SpringBootTest(properties = {"merenda.demo.horario=12:00", "spring.datasource.url=jdbc:h2:mem:filarefeicao;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})`, `@ActiveProfiles("test")`): cria turmas `T-INT` (`INTEGRAL`) e `T-MAN` (`MANHA`), alunos `ALU-T01`/`ALU-T02` enturmados hoje e cardápio de hoje do Almoço. `integralAutorizadoEParcialBarradoNoAlmoco`: `ALU-T01` → AUTORIZADO "Almoço"; logo de novo → `CONSUMO_RECENTE_BLOQUEADO`; `ALU-T02` → `REFEICAO_NAO_PREVISTA_PARA_TURMA`.
- [ ] **Step 2:** rodar as duas classes → FAIL.
- [ ] **Step 3:** implementar e apagar `TurnoResolver`.
- [ ] **Step 4:** as duas passam; `$MVN test` dá BUILD SUCCESS.
- [ ] **Step 5:** `git -C api-merenda-escolar diff --stat`.

---

### Task 7: API — fechamento (medição de sobra) por refeição

**Files:**
- Modify: `.../merenda/medicao/api/MedicaoSobraRequest.java`, `MedicaoSobraResponse.java`, `MedicaoSobraController.java`; `.../medicao/application/MedicaoSobraService.java`; `.../medicao/infrastructure/MedicaoSobraRepository.java`
- Test: `.../merenda/medicao/application/MedicaoSobraServiceTest.java` (novo)

**Interfaces:**
- Consumes: `MedicaoSobra` novo (Task 3), `CardapioRepository.findByDataAndTipoRefeicaoIdAndAtivoTrue` (Task 5), `GradeRefeicoesService.buscar` (Task 1).
- Produces:
  - `MedicaoSobraRequest(@NotNull LocalDate data, @NotNull UUID tipoRefeicaoId, @NotNull UUID receitaId, …porções)`; `MedicaoSobraResponse(UUID id, LocalDate data, UUID tipoRefeicaoId, UUID receitaId, int, int, int, int, String origem)`.
  - `GET /api/v1/medicoes-sobra?data=&tipoRefeicaoId=`.
  - Repositório: `findByDataAndTipoRefeicaoIdAndEscolaIdAndReceitaId(LocalDate, UUID, int, UUID)` e `findAllByDataAndTipoRefeicaoIdAndEscolaIdOrderByReceitaId(LocalDate, UUID, int)`. As queries de análise mudam na Task 9; os métodos de escrita por turno ficam até a Task 12.
  - `MedicaoSobraService.listar(LocalDate, UUID)`. Mensagens: `"Todos os itens do fechamento devem usar a mesma data e refeição"` e `"Receita %s não pertence ao cardápio de %s (%s)"` (receita, data, nome da refeição).

- [ ] **Step 1: Testes que falham** (`MedicaoSobraServiceTest`, Mockito):
  - `relancarMesmoItemNaMesmaRefeicaoSobrescreve`: existente encontrado → salvo com o mesmo id.
  - `mesmaReceitaEmRefeicoesDiferentesSaoMedicoesDistintas`: a busca com `LANCHE_DA_MANHA.id()` vem vazia → id novo, mesmo existindo a do Almoço.
  - `fechamentoComRefeicoesDiferentesEhRecusado`: `MedicaoSobraInconsistenteException` com a mensagem acima.
  - `receitaForaDoCardapioDaRefeicaoEhRecusada`: mensagem termina em `(Almoço)`.
- [ ] **Step 2:** `$MVN test -Dtest=MedicaoSobraServiceTest -Dsurefire.failIfNoSpecifiedTests=false` → FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** a classe passa; `$MVN test` dá BUILD SUCCESS.
- [ ] **Step 5:** `git -C api-merenda-escolar diff --stat`.

---

### Task 8: API — histórico do legado por refeição

**Files:**
- Modify: `.../merenda/historico/infrastructure/HistoricoConsumoRepository.java`, `HistoricoFakeLoader.java`; `.../historico/application/HistoricoConsumoService.java`
- Modify (ponte temporária): `.../merenda/gestao/application/IndicadoresDadosService.java:73`, `.../merenda/relatorio/application/RelatorioIAWorker.java:186-192,265`
- Test: `.../merenda/historico/application/HistoricoConsumoServiceTest.java`, `.../merenda/historico/infrastructure/HistoricoPorRefeicaoTest.java` (novo)

**Interfaces:**
- Consumes: `HistoricoConsumo` novo e `RefeicaoPadrao.doNomeLegado` (Task 3).
- Produces:
  - As queries `execucoesNaJanela(LocalDate inicio, LocalDate fim, UUID tipoRefeicaoId)`, `taxaPorItem(UUID tipoRefeicaoId, …)`, `taxaPorReceitas(UUID tipoRefeicaoId, …)` e `serieDiariaPorReceita(UUID tipoRefeicaoId, …)` filtram `h.tipoRefeicaoId = :tipoRefeicaoId`.
  - `long countByTipoRefeicaoIdIsNullAndDataBetween(LocalDate inicio, LocalDate fim)`.
  - `HistoricoConsumoService.rankingDosPioresItens(LocalDate, UUID)`, `itensDoCardapioComTaxa(LocalDate, UUID, List<UUID>)`, `serieDiariaDosItens(LocalDate, UUID, List<UUID>)`.
  - O `HistoricoFakeLoader` grava pelo construtor novo, com `doNomeLegado(campos[4]).map(RefeicaoPadrao::id).orElse(null)`, e ignora a coluna `turno` do TSV.
  - Ponte até a Task 9: `IndicadoresDadosService` passa `RefeicaoPadrao.doTurnoLegado(turno).id()`; o worker passa `relatorio.getTipoRefeicaoId()`.

- [ ] **Step 1: Testes que falham.**
  - `HistoricoConsumoServiceTest`: os cinco testes passam a usar `ALMOCO.id()` e verificam o repositório chamado com esse id.
  - `HistoricoPorRefeicaoTest` (`@SpringBootTest`, `@ActiveProfiles("test")`, H2 próprio; o `HistoricoFakeLoader` roda no `ApplicationReady`), `historicoDoLegadoEhMapeadoPorNomeDeRefeicao`: existe linha com `ALMOCO.id()` e com `CAFE_DA_MANHA.id()`; `countByTipoRefeicaoIdIsNullAndDataBetween(2000-01-01, 2100-01-01) > 0`; nenhuma linha `"Almoco - EMEIS"` fica com refeição diferente de Almoço (JPQL).
- [ ] **Step 2:** rodar as duas classes → FAIL.
- [ ] **Step 3:** implementar e aplicar a ponte.
- [ ] **Step 4:** as duas passam; `$MVN test` dá BUILD SUCCESS.
- [ ] **Step 5:** `git -C api-merenda-escolar diff --stat`.

---

### Task 9: API — gestão, indicadores e contrato com o Python por refeição

**Files:**
- Create: `.../merenda/analytics/application/port/RefeicaoRef.java`
- Modify: `.../gestao/api/ConsolidacaoConsumoResponse.java`, `GestaoController.java`, `IndicadoresController.java`; `.../gestao/application/ConsolidacaoConsumoService.java`, `CardapioAnaliseService.java`, `CardapioNaoEncontradoException.java`, `IndicadoresDadosService.java`, `IndicadoresLogisticosService.java`
- Modify: `.../analytics/application/port/IndicadoresLogisticosInput.java`, `IndicadoresLogisticos.java`, `PrevisaoConsumoInput.java`; `.../analytics/infrastructure/python/PythonIndicadoresLogisticosAdapter.java`, `PythonPrevisaoConsumoAdapter.java`
- Modify: `.../inteligencia/application/port/AnaliseLogisticaInput.java`, `IndicadoresParaLLM.java`; `.../inteligencia/infrastructure/fake/FakeAnaliseLogisticaAdapter.java:46`
- Modify: `.../fila/infrastructure/AuditoriaConsumoRepository.java` (agregados), `.../medicao/infrastructure/MedicaoSobraRepository.java` (queries de análise), `.../relatorio/application/RelatorioIAWorker.java`
- Modify (ponte até a Task 10): `.../relatorio/application/RelatorioIAService.java:59-90`; (ponte até a Task 11): `.../shared/config/DadosDemonstracaoLoader.java:390`
- Test: `ConsolidacaoConsumoServiceTest`, `IndicadoresDadosServiceTest`, `IndicadoresLogisticosServiceTest`, `PythonIndicadoresLogisticosAdapterTest`, `PythonPrevisaoConsumoAdapterTest`, `IndicadoresParaLLMTest`, `ConclusaoDeterministicaTest`, `FakeAnaliseLogisticaAdapterTest`, `RelatorioIAWorkerTest`, `DadosDemonstracaoTurmasTest` (só a troca de turno por id)

**Interfaces:**
- Consumes: `GradeRefeicoesService.buscar` (Task 1); `CardapioRepository` por refeição (Task 5); histórico por refeição (Task 8).
- Produces:
  - `record RefeicaoRef(UUID id, String nome)`.
  - `ConsolidacaoConsumoResponse(LocalDate data, UUID tipoRefeicaoId, String refeicao, UUID cardapioId, …demais campos iguais)`; `ConsolidacaoConsumoService.consolidar(LocalDate, UUID tipoRefeicaoId)`; `CardapioNaoEncontradoException(LocalDate, String refeicao)`: `"Não existe cardápio ativo para %s em %s."`.
  - Endpoints: `GET /api/v1/gestao/consolidacoes?data=&tipoRefeicaoId=`, `GET /api/v1/gestao/cardapios-analise?inicio=&fim=&tipoRefeicaoId=`, `GET /api/v1/gestao/indicadores?data=&tipoRefeicaoId=[&receitaIds&datas]`.
  - `IndicadoresLogisticosInput`: `Turno turno` vira `RefeicaoRef refeicao`, e ganha `long historicoSemRefeicao` no fim (o construtor curto recebe `0`). Serializa `"refeicao":{"id":…,"nome":…}` e `"historicoSemRefeicao"`.
  - `IndicadoresLogisticos`: ganha `RefeicaoRef refeicao` depois de `turno`, que fica nullable só para as fotografias v4. `indisponivel(LocalDate, RefeicaoRef)` usa `schemaVersion "5"`.
  - O adapter de indicadores aceita só `"5"` e exige `result.refeicao() != null && input.refeicao().id().equals(result.refeicao().id())`.
  - `PrevisaoConsumoInput`: `Turno turno` vira `String refeicao` (nome). O adapter envia `"refeicao"`, sem `"turno"`.
  - `AnaliseLogisticaInput`: `Turno turno` vira `String refeicao`. `IndicadoresParaLLM`: `Turno turno` vira `RefeicaoRef refeicao`; aviso de turma `"...continuam da refeição inteira, porque resto no prato não é medido por turma."`. Fake: `"Conferir se o atendimento da refeição foi encerrado…"`.
  - `IndicadoresLogisticosService.calcular(LocalDate, UUID tipoRefeicaoId[, List<UUID>[, List<LocalDate>]])`; `IndicadoresDadosService.preparar(...)` com a mesma troca, preenchendo `historicoSemRefeicao` com `countByTipoRefeicaoIdIsNullAndDataBetween(inicio, data)`.
  - `AuditoriaConsumoRepository`: `atendimentosPorTurma(LocalDate, UUID tipoRefeicaoId)`, `countByDataOperacionalAndTipoRefeicaoIdAndResultado(LocalDate, UUID, ResultadoConsumo)` e `consumosPorDia(UUID tipoRefeicaoId, LocalDate ate, Pageable)` substituem os de turno.
  - `MedicaoSobraRepository`: `resumoDoDia`, `restoPorReceita`, `restoPorReceitaNasDatas`, `datasComFechamentoCompleto`, os `count*` e `datasComFechamento*` mantêm o nome e trocam `Turno turno` por `UUID tipoRefeicaoId` (`m.tipoRefeicaoId`).
  - Worker: consolida e calcula com `relatorio.getTipoRefeicaoId()`; previsão com `consolidacao.refeicao()`; série diária com `consumosPorDia(tipoRefeicaoId, …)`. Em base antiga isso já cobre "só dias com consumo".
  - Pontes: `RelatorioIAService.validarAmostra` usa `doTurnoLegado(request.turno()).id()`; `DadosDemonstracaoLoader.gerarConsumos` usa `cardapio.getTipoRefeicaoId()` no `countBy…`.

- [ ] **Step 1: Testes que falham.**
  - `ConsolidacaoConsumoServiceTest.consolidaPorRefeicao`: contagens pedidas com `(data, ALMOCO.id(), …)`, resposta com `refeicao == "Almoço"`.
  - `IndicadoresDadosServiceTest.separaConsumosPorRefeicaoNoMesmoDia`: aluno de turma `INTEGRAL` autorizado no café e no almoço → `preparar(data, ALMOCO.id())` com `consumosRegistrados == 1` e `turmas.size() == 1`. Os quatro testes existentes passam a criar cardápio/consumo por refeição.
  - `IndicadoresDadosServiceTest.historicoSemRefeicaoVaiNoContrato`: data de referência `2031-05-20` (o TSV vai de 2025-09-26 a 2026-09-21), duas linhas de histórico com `tipoRefeicaoId` nulo e uma de Almoço na janela, cardápio do Almoço no dia → `historicoSemRefeicao() == 2`.
  - `PythonIndicadoresLogisticosAdapterTest`: o corpo enviado tem `"refeicao":{"id":"5e1a0000-0000-4000-8000-000000000003","nome":"Almoço"}` e não tem `"turno"`; `aceitaRespostaSchema5`; `rejeitaRespostaSchema4`; `rejeitaRespostaDeOutraRefeicao` (id diferente → `IllegalStateException`); `fallbackIndisponivelUsaSchema5`.
  - `PythonPrevisaoConsumoAdapterTest`: corpo com `"refeicao":"Almoço"` e sem `"turno"`.
  - `IndicadoresParaLLMTest`: o aviso com turma escolhida contém `"refeição inteira"`.
  - `RelatorioIAWorkerTest`: `consolidacaoService.consolidar(data, LANCHE_DA_MANHA.id())` para relatório criado com `Turno.MANHA`.
  - `DadosDemonstracaoTurmasTest`: `countFechamentos(…, RefeicaoPadrao.doTurnoLegado(turno).id())`, só a troca de tipo.
- [ ] **Step 2:** `$MVN test` → falhas de compilação e asserção.
- [ ] **Step 3:** implementar e aplicar as pontes.
- [ ] **Step 4:** `$MVN test` dá BUILD SUCCESS.
- [ ] **Step 5:** `git -C api-merenda-escolar diff --stat`.

---

### Task 10: API — relatório de IA por (data, refeição), com os antigos legíveis

**Files:**
- Modify: `.../relatorio/api/CriarRelatorioIARequest.java`, `RelatorioIAResponse.java`, `RelatorioIAController.java`; `.../relatorio/application/RelatorioIAService.java`; `.../relatorio/domain/RelatorioIA.java`; `.../relatorio/infrastructure/RelatorioIARepository.java`
- Test: `.../relatorio/api/RelatorioIAHttpTest.java`, `.../relatorio/application/RelatorioIndicadoresTest.java`, `RelatorioIAWorkerTest`, `.../relatorio/domain/RelatorioIATest.java`

**Interfaces:**
- Consumes: `GradeRefeicoesService` (Task 1); queries de medição por refeição (Task 9).
- Produces:
  - `CriarRelatorioIARequest(@NotNull LocalDate dataReferencia, @NotNull UUID tipoRefeicaoId, List<UUID> receitaIds, List<LocalDate> datasSelecionadas, UUID turmaId)`.
  - `RelatorioIA.pendente(LocalDate, UUID tipoRefeicaoId, Instant, List<UUID>, List<LocalDate>, UUID turmaId)` mais as sobrecargas curtas com `UUID`. As de `Turno` saem. `turno` vira só leitura e fica nulo nos novos. `promptVersao = "v3-refeicoes"`.
  - `RelatorioIAResponse`: depois de `Turno turno`, ganha `UUID tipoRefeicaoId` e `String refeicao` (nome pela grade; nulo se a refeição for nula).
  - `GET /api/v1/relatorios-ia?data=&tipoRefeicaoId=` → `findTop20ByDataReferenciaAndTipoRefeicaoIdOrderByCriadoEmDesc`.
  - `validarAmostra` conta por refeição; `buscar(tipoRefeicaoId)` antes, para dar 404. Mensagem: `"O relatório de IA precisa de pelo menos 20 dias com fechamento %s nesta refeição (%s); há %d."`, com `"nos últimos 30 dias"`/`"entre as datas selecionadas"` e o nome. A mensagem por receita fica igual.

- [ ] **Step 1: Testes que falham.**
  - `RelatorioIAHttpTest.amostraInsuficienteRetorna422ComQuantidadeDeDiasNoDetalhe`: corpo `{"dataReferencia":"2001-03-14","tipoRefeicaoId":"5e1a0000-0000-4000-8000-000000000003"}` → 422, `detail == "O relatório de IA precisa de pelo menos 20 dias com fechamento nos últimos 30 dias nesta refeição (Almoço); há 0."`.
  - `refeicaoForaDaGradeRetorna404`: id aleatório → 404 com `detail` `"Refeição <id> não existe na grade."`.
  - `amostraContaSoDiasDaRefeicao`: 20 medições de Almoço em dias distintos de março de 2002 e nenhuma de Café → POST Almoço 202 e Café 422 `"… há 0."`.
  - `RelatorioIndicadoresTest.relatorioAntigoComTurnoContinuaLegivelEListado`: insere por SQL nativo um `relatorio_ia` com `turno='MANHA'`, `tipo_refeicao_id` de Lanche da manhã (como deixa a V15), status `CONCLUIDO` e `indicadores_json` v4 com `"turno":"MANHA"`. `buscar(id)` → `turno == MANHA`, `refeicao == "Lanche da manhã"`, `indicadores().turno() == MANHA`, `indicadores().refeicao() == null`. `listar(data, LANCHE_DA_MANHA.id())` contém o id.
  - `RelatorioIATest` e `RelatorioIAWorkerTest` passam a usar `pendente(..., ALMOCO.id(), ...)`.
- [ ] **Step 2:** rodar as quatro classes → FAIL.
- [ ] **Step 3:** implementar e remover a ponte da Task 9 em `validarAmostra`.
- [ ] **Step 4:** as classes passam; `$MVN test` dá BUILD SUCCESS.
- [ ] **Step 5:** `git -C api-merenda-escolar diff --stat`.

---

### Task 11: API — dados de demonstração com turmas parciais e integrais e consumo por refeição

**Files:**
- Modify: `.../merenda/shared/config/DadosDemonstracaoLoader.java`
- Test: `.../shared/config/DadosDemonstracaoTurmasTest.java`, `.../shared/config/DadosDemonstracaoFimDeSemanaTest.java`

**Interfaces:**
- Consumes: `GradeRefeicoesService.listar()` (Task 1; vira dependência do construtor do loader); construtores novos (Task 3); repositórios por refeição (Tasks 5, 7, 9).
- Produces (regras):
  - Turmas `1A`–`3A` `MANHA`, `4A`–`6A` `TARDE`, `7A`–`9A` `INTEGRAL`. Aluno sem vínculo vai para `1 + numero % 9`, exceto **aluno com rosto cadastrado, que vai para `7A`**: a demonstração facial passa em qualquer refeição diurna.
  - Dia útil: cardápio em cada refeição com alguma turma da demonstração (café, lanche da manhã, almoço, lanche da tarde). Almoço usa `catalogo.cardapioPrincipal`, as outras `sortearCardapio`. `quantidadePlanejada` = alunos elegíveis no dia (vigente numa turma cujo turno a refeição atende), mínimo 1.
  - Hoje: cardápio nas 5 refeições, inclusive Lanche da noite e fim de semana.
  - Consumo só de elegíveis, com idempotência por `(dia, refeição)` via `countByDataOperacionalAndTipoRefeicaoIdAndResultado`. A primeira leitura cai em `inicio + rng.nextInt(max(1, minutosDaJanela − 20))` minutos; a repetição, de 3 a 17 minutos depois. Chave `aluno:dia:tipoRefeicaoId`. Nada depois de `agora`, e aluno com rosto continua fora.
  - Medição sintética (escola 1) em todo dia útil das 4 refeições com turma. Hoje, só o Almoço já nasce fechado; as outras ficam para a tela.
  - Sai `TURNOS_COM_MOVIMENTO` e `instanteNoTurno`; `corrigirSeNecessario` reconhece o cardápio principal por `tipoRefeicaoId == ALMOCO.id()`, não mais por `Turno.INTEGRAL`. As pontes da Task 9 saem.

- [ ] **Step 1: Testes que falham** (`DadosDemonstracaoTurmasTest`, `HOJE = 2030-03-13` 10:00):
  - `criaNoveTurmasComRegimeParcialEIntegral`: códigos e turnos como acima.
  - `consumoSoParaQuemTemDireitoNaRefeicao`: em toda auditoria AUTORIZADA, o turno da turma carimbada (join em `Turma`) está entre os turnos da refeição.
  - `consumoCaiDentroDaJanelaDaRefeicao`: a hora local de cada primeira leitura (`chaveConsumoAutorizado` não nula) está em `[inicio, fim)`.
  - `alunoIntegralConsomeEmMaisDeUmaRefeicaoNoMesmoDia`: existe `(aluno, dia)` com autorizações em ao menos 2 `tipoRefeicaoId`.
  - `cardapioDeHojeExisteEmTodasAsRefeicoes`: as 5.
  - `refeicoesComTurmaTemVinteDiasDeFechamentoNosUltimosTrintaDias`: `countFechamentos(HOJE−29, HOJE, id) >= 20` para café, lanche da manhã, almoço e lanche da tarde.
  - `fechamentoDeHojeSoNoAlmoco`: hoje, `countFechamentos == 1` no Almoço e `0` nas outras.
  - `alunoComRostoEntraNaTurmaIntegralEFicaForaDoSintetico`: `ALU-9001` vigente em `7A` e com zero auditorias.
  - Os testes de repetição e de chave única passam a agrupar por `a.tipoRefeicaoId`.
  - `DadosDemonstracaoFimDeSemanaTest.cardapioDeHojeExisteEmTodasAsRefeicoesNoSabado`.
- [ ] **Step 2:** rodar as duas classes → FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** as duas passam; `$MVN test` dá BUILD SUCCESS.
- [ ] **Step 5:** `git -C api-merenda-escolar diff --stat`.

---

### Task 12: Contract — `turno` sai das tabelas, das entidades e do contrato Python

**Files:**
- Create: `api-merenda-escolar/src/main/resources/db/migration/V16__remove_turno_das_tabelas.sql`
- Modify: `Cardapio`, `AuditoriaConsumo`, `MedicaoSobra`, `HistoricoConsumo` (sem campo, construtor e `atualizar` com `Turno`); `CardapioRepository`, `MedicaoSobraRepository` (sem métodos com `Turno`); `.../medicao/infrastructure/MedicaoSobraFakeLoader.java` (construtor novo com `doTurnoLegado(Turno.valueOf(campos[2])).id()`)
- Delete: `.../refeicao/domain/TurnoLegadoNasEntidadesTest.java`
- Modify: testes que ainda usam os construtores com `Turno` (`.../medicao/domain/MedicaoSobraTest.java`, `.../cardapio/infrastructure/CardapioReceitaBackfillTest.java` e os que o compilador apontar) passam aos construtores novos
- Modify: `analytics-merenda-escolar/app/domain/indicadores_logisticos.py`, `previsao_consumo.py` e os testes Python que usam `turno`
- Test: `MigracaoRefeicoesTest` (continua passando), suítes completas

**Interfaces:**
- Produces:
  - V16: `DROP INDEX idx_cardapio_data_turno_ativo`, `idx_auditoria_data_turno`, `idx_medicao_sobra_data_turno`, `idx_historico_data_turno`; `ALTER TABLE medicao_sobra DROP CONSTRAINT uk_medicao_sobra`; `DROP COLUMN turno` em `cardapio`, `auditoria_consumo`, `medicao_sobra`, `historico_consumo`. `relatorio_ia.turno` fica.
  - Python: `IndicadoresRequest.refeicao: RefeicaoRef` obrigatório e sem `turno`, de modo que `extra="forbid"` recusa `turno`; a resposta perde `turno`. `PrevisaoConsumoRequest.refeicao: str` obrigatório e sem `turno`.
  - `grep -rn "Turno" api-merenda-escolar/src/main/java` sobra só em: `turma/**`, `fila/domain/Turno.java`, `refeicao/**`, `relatorio/domain/RelatorioIA.java`, `relatorio/api/RelatorioIAResponse.java`, `analytics/application/port/IndicadoresLogisticos.java`, `medicao/infrastructure/MedicaoSobraFakeLoader.java`.

- [ ] **Step 1: Ajustar os testes Python:** `_request`/`payload` usam `refeicao`; novo `test_turno_nao_e_mais_aceito` (POST com `"turno":"MANHA"` além da refeição → 422); previsão sem `refeicao` → 422.
- [ ] **Step 2:** pytest → FAIL.
- [ ] **Step 3:** implementar o contract Python e depois o da API (migration, entidades, repositórios, fake loader).
- [ ] **Step 4:** `./.venv/bin/python -m pytest -q` verde; `$MVN test` dá BUILD SUCCESS, com `MigracaoRefeicoesTest` passando sobre V14→V16; o `grep` acima confere.
- [ ] **Step 5:** `git -C api-merenda-escolar diff --stat` e `git -C analytics-merenda-escolar diff --stat`.

---

### Task 13: Front — fila mostra a refeição e traduz os motivos novos

**Files:**
- Modify: `front-merenda-escolar/src/app/features/fila/models/fila.models.ts`, `fila/application/motivos.ts`, `fila/pages/fila-page/fila-page.component.ts:82`, `fila-page.component.html:13`
- Test: `fila/application/motivos.spec.ts`, `fila/pages/fila-page/fila-page.component.spec.ts`

**Interfaces:**
- Consumes: `ValidarConsumoResponse` da Task 6.
- Produces:
  - `ValidarConsumoResponse` sem `turno`, com `tipoRefeicaoId: string | null` e `refeicao: string | null`.
  - Motivos: `FORA_DO_HORARIO_DE_REFEICAO: 'Fora do horário de refeição.'`, `REFEICAO_NAO_PREVISTA_PARA_TURMA: 'Esta refeição não é servida para a turma do aluno.'`, `GRADE_DE_REFEICOES_INVALIDA: 'Grade de refeições não configurada ou inconsistente. Avise a coordenação.'`.
  - Detalhe do anúncio autorizado: `` `${refeicao} • ${cardapio}` `` (só `cardapio` se `refeicao` vier nula).
  - Subtítulo: `'A validação é feita sem IA. A refeição é identificada pelo horário e pela turma do aluno.'`

- [ ] **Step 1: Testes que falham:** `motivos.spec` `traduz os motivos de refeição` (os três textos acima); `fila-page.spec` `autorizada anuncia o aluno com a refeição e o cardápio` (flush com `refeicao: 'Almoço', cardapio: 'Arroz'` → `detalhe: 'Almoço • Arroz'`); `bloqueada fora da refeição da turma traduz o motivo` (`REFEICAO_NAO_PREVISTA_PARA_TURMA` com `alunoNome: 'Aluno Teste'` → título `'Aluno Teste'`, detalhe traduzido). Os fixtures perdem `turno`.
- [ ] **Step 2:** comando de testes do front → FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** testes do front verdes e `tsc` sem erro.
- [ ] **Step 5:** `git -C front-merenda-escolar diff --stat`.

---

### Task 14: Front — cardápio, fechamento e dashboard por refeição

**Files:**
- Create: `front-merenda-escolar/src/app/shared/refeicao/tipo-refeicao.models.ts`, `tipo-refeicao-api.service.ts`
- Modify: `features/cardapio/models/cardapio.models.ts`, `cardapio/components/cardapio-form/*`, `cardapio/pages/cardapio-page/*`
- Modify: `features/fechamento/models/fechamento.models.ts`, `fechamento/data-access/fechamento-api.service.ts`, `fechamento/pages/fechamento-page/*`
- Modify: `features/dashboard/models/dashboard.models.ts`, `dashboard/models/indicadores.models.ts`, `dashboard/data-access/dashboard-api.service.ts`, `dashboard/pages/dashboard-page/*`, `dashboard/components/indicadores-logisticos/*`
- Test: os `*.spec.ts` dessas pastas e `shared/refeicao/tipo-refeicao-api.service.spec.ts`

**Interfaces:**
- Consumes: `GET /api/v1/tipos-refeicao` (Task 1) e os contratos das Tasks 5, 7, 9 e 10.
- Produces:
  - `interface TipoRefeicao { id: string; nome: string; ordem: number; inicio: string; fim: string; turnos: string[] }`; `function rotuloRefeicao(t: TipoRefeicao): string` → `'Almoço (11:30–13:00)'`; `TipoRefeicaoApiService.listar(): Observable<TipoRefeicao[]>`.
  - Cardápio: `Cardapio`/`CardapioRequest` trocam `turno` por `tipoRefeicaoId` (`Cardapio` também tem `refeicao: string`). O tipo `Turno` sai de `cardapio.models.ts`. O form recebe `tiposRefeicao = input<TipoRefeicao[]>([])`, com select `Refeição` obrigatório; o campo texto `nomeRefeicao` passa a se chamar `Nome do cardápio`. A coluna `Turno` da página vira `Refeição` e mostra `cardapio.refeicao`. A página carrega os tipos.
  - Fechamento: select `Refeição` (default o primeiro por `ordem`); `MedicaoSobraRequest/Response` com `tipoRefeicaoId`; `listarMedicoes(data, tipoRefeicaoId)`; `criarRelatorio({ dataReferencia, tipoRefeicaoId })`. Textos: título `Fechamento da refeição`, `Carregar refeição`, `Cardápio da refeição`, `Não há cardápio cadastrado para esta data e refeição.`, `Fechamento já realizado para esta data e refeição.`, `Fechar refeição e gerar relatório`, `Refeição fechada.`, `Refeição fechada e relatório solicitado. Acompanhe no Dashboard — aceitação e desperdício passam a ser calculados a partir do que foi registrado aqui.`
  - Dashboard: `buscarConsolidacao(data, tipoRefeicaoId)`, `buscarIndicadores(data, tipoRefeicaoId, …)`, `buscarCardapiosAnalise(inicio, fim, tipoRefeicaoId)`, `listarRelatorios(data, tipoRefeicaoId)`. `CriarRelatorioIARequest.tipoRefeicaoId`, `ConsolidacaoConsumo { tipoRefeicaoId; refeicao }`. `RelatorioIA` ganha `tipoRefeicaoId`, `refeicao` e `turno: string | null`; `IndicadoresLogisticos` ganha `turno?: string | null` e `refeicao?: { id: string; nome: string } | null`. Filtro `Refeição` em vez de `Turno escolar`: `ngOnInit` carrega os tipos, seleciona o primeiro e só então consulta; falha ao listar mostra `'Não foi possível carregar as refeições.'`. Os textos "turno" do dashboard e do componente de indicadores viram "refeição" (`Relatórios salvos para esta data e refeição`, `Cardápio da refeição`, `Mesmo aluno, mesma refeição`, `…continuam da refeição inteira…`). O rótulo do indicador é `dados().refeicao?.nome ?? 'Turno ' + dados().turno`.

- [ ] **Step 1: Testes que falham.**
  - `tipo-refeicao-api.service.spec`: `GET …/tipos-refeicao`.
  - `cardapio-form.spec`: `escolhe a refeição da grade e envia tipoRefeicaoId` (opção `'Almoço (11:30–13:00)'`, emite `tipoRefeicaoId: 'id-almoco'`); `nao envia sem refeição escolhida`.
  - `cardapio-page.spec`: fixtures com `tipoRefeicaoId`/`refeicao`; `mostra a refeição na listagem` (célula `'Almoço'`).
  - `fechamento-page.spec`: `carrega o cardápio da refeição escolhida` (`buscarConsolidacao(DATA, 'id-almoco')`, `listarMedicoes(DATA, 'id-almoco')`); `registra uma medicao por item e so entao gera o relatorio` passa a conferir `tipoRefeicaoId` nas medições e `criarRelatorio({ dataReferencia: DATA, tipoRefeicaoId: 'id-almoco' })`; as notificações esperam `'Refeição fechada.'`.
  - `dashboard-page.spec`: `consulta pela primeira refeição da grade ao abrir`; `ignora a resposta de um filtro anterior após a troca de refeição` (`atualizarFiltros('refeicao', 'id-cafe')`); os demais testes ganham o mock de `TipoRefeicaoApiService`.
  - `indicadores-logisticos.component.spec`: `mostra o nome da refeição e cai no turno em fotografia antiga` (`refeicao.nome 'Almoço'` → `'Almoço'`; só `turno: 'MANHA'` → `'Turno MANHA'`).
- [ ] **Step 2:** testes do front → FAIL.
- [ ] **Step 3:** implementar. `atualizarFiltros(campo: 'data' | 'refeicao', valor)`.
- [ ] **Step 4:** testes do front verdes e `tsc` sem erro.
- [ ] **Step 5:** `git -C front-merenda-escolar diff --stat`.

---

### Task 15: Documentação

**Files:**
- Modify: `api-merenda-escolar/README.md` (módulo `refeicao`; fluxo da fila com os 3 motivos novos; tabela de endpoints com `tipoRefeicaoId` e `GET /api/v1/tipos-refeicao`; `merenda.demo.horario` em "Outras propriedades"; migrations `V1` a `V16`; dados de demonstração com turmas parciais/integrais, refeições e a regra do rosto em `7A`; relatório e amostra por refeição; contrato `schemaVersion=5`; roteiro da demonstração com o horário). Também `api-merenda-escolar/DECISOES-TECNICAS.md`, nova seção `## 10. Refeição como unidade da fila e da análise` (regime pelo turno da turma, janela `[início, fim)`, relógio de demonstração com deslocamento, mapeamento do legado e o que ficou fora).
- Modify: `analytics-merenda-escolar/README.md` (entrada `refeicao`/`historicoSemRefeicao`, saída v5, exemplo de previsão com `refeicao`) e `front-merenda-escolar/README.md` (Cardápios, `Fechamento da refeição`, filtro do Dashboard).

- [ ] **Step 1:** atualizar os quatro arquivos. Exemplos com dados inventados; nenhum texto cita ticket.
- [ ] **Step 2:** `grep -n -i "turno" <arquivos>` só deixa ocorrências corretas: turno da turma, relatório antigo, legado.
- [ ] **Step 3:** `git diff --stat` nos três repositórios.

---

### Task 16: Validação final ponta a ponta

**Files:** nenhum código. Só execução.

- [ ] **Step 1: Suítes.** `$MVN test`, pytest e testes + `tsc` do front, todos verdes.
- [ ] **Step 2: Recriar a base, com o Frank.** Pedir que ele pare a API do IntelliJ (porta 8081) e esperar a confirmação. Não encerrar o processo dele. Conferir com `ss -ltnp | grep 8081` que a porta está livre e então `rm -f api-merenda-escolar/data/merenda*`.
- [ ] **Step 3: Serviços.** Analytics (8000) e front (4200) só sobem se `ss -ltnp` mostrar que não estão no ar (comandos dos READMEs). A API sobe em segundo plano, a partir de `api-merenda-escolar`: `JAVA_HOME=$HOME/.jdks/corretto-25.0.4.1 $HOME/.m2/wrapper/dists/apache-maven-3.9.16-bin/5grr65jo27hi51sujmtcldfovl/apache-maven-3.9.16/bin/mvn -B spring-boot:run -Dspring-boot.run.arguments=--merenda.demo.horario=07:10`. Esperar `GET /actuator/health` = UP. Este processo é nosso e pode ser parado entre os horários.
- [ ] **Step 4: Massa de teste pela API.** `POST /api/v1/turmas` `E2E-INT` (`INTEGRAL`) e `E2E-MAN` (`MANHA`); `POST /api/v1/alunos` `ALU-E2E-01` (matrícula `E2E0001`, turma `E2E-INT`) e `ALU-E2E-02` (`E2E0002`, `E2E-MAN`), os dois com `dataInicio` = hoje. Ficam fora do consumo sintético.
- [ ] **Step 5: 07:10.** `POST /api/v1/fila/validacoes` QR `ALU-E2E-01` → 200, `AUTORIZADO`, `refeicao == "Café da manhã"`; `ALU-E2E-02` → `BLOQUEADO`, `REFEICAO_NAO_PREVISTA_PARA_TURMA`.
- [ ] **Step 6: 12:00** (parar a nossa API e subir com `--merenda.demo.horario=12:00`):
  - `ALU-E2E-01` → `AUTORIZADO` `"Almoço"` (não bloqueia por ter comido no café); repetir na hora → `CONSUMO_RECENTE_BLOQUEADO`; esperar 125 s (Monitor com laço de espera, não `sleep` em primeiro plano) e repetir → `AUTORIZADO`.
  - `ALU-E2E-02` → `REFEICAO_NAO_PREVISTA_PARA_TURMA`.
  - Fechamento do Lanche da manhã de hoje: receitas de `GET /api/v1/cardapios` (item de hoje com `tipoRefeicaoId` `…0002`) → `POST /api/v1/medicoes-sobra/fechamento` → 200; `POST /api/v1/relatorios-ia` `{dataReferencia: hoje, tipoRefeicaoId: …0002}` → 202; consultar `GET /api/v1/relatorios-ia/{id}` até `CONCLUIDO`, com `refeicao == "Lanche da manhã"`, `indicadores.schemaVersion == "5"` e `indicadores.refeicao.nome == "Lanche da manhã"`; `GET /api/v1/relatorios-ia?data=hoje&tipoRefeicaoId=…0002` lista o id.
- [ ] **Step 7: Tela (ainda às 12:00).** Usar a skill `betha-chrome-devtools:navegador` antes da primeira chamada de browser. Conferir: em Fila, modo facial, **Usar imagem** com `api-merenda-escolar/demo/rostos-teste/ALU-9001-teste.jpg` → verde, "Helena Duarte", detalhe começando com "Almoço •". Em Cardápio, a coluna e o select `Refeição` com as 5 opções. Em Fechamento, a refeição "Lanche da manhã" mostra "Fechamento já realizado para esta data e refeição.". No Dashboard, filtro `Refeição` = Lanche da manhã, indicadores carregados e o relatório do Step 6 no histórico. Console sem erro.
- [ ] **Step 8: 14:00** (reiniciar a nossa API com `--merenda.demo.horario=14:00`): `ALU-E2E-01` → 200, `BLOQUEADO`, `FORA_DO_HORARIO_DE_REFEICAO`.
- [ ] **Step 9: Encerrar.** Parar só a API que subimos e avisar o Frank que ele pode subir a dele de novo no IntelliJ (sem `merenda.demo.horario` ela usa o relógio real). Relatar o resultado de cada passo, `git diff --stat` dos três repositórios e lembrar que nada foi commitado.
