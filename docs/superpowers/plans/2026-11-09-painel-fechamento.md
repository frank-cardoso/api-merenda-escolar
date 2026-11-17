# Painel do dia no Fechamento Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Situação de cada refeição do dia no Fechamento, abertura na pendência e aviso de fechamentos pendentes no Dashboard.

**Architecture:** Um serviço de domínio na API calcula a situação de (data, refeição) a partir da grade, do cardápio, das medições e do `Clock`; dois endpoints em `/api/v1/fechamentos` o expõem. O front ganha um componente de painel no Fechamento e um aviso no Dashboard, ambos via um novo `FechamentoPainelApiService`.

**Tech Stack:** Spring Boot 3.5 / Java 25 / H2 / JUnit 5 + AssertJ + MockMvc; Angular 20 (signals, standalone) + Karma/Jasmine.

**Spec:** `api-merenda-escolar/docs/superpowers/specs/2026-11-09-painel-fechamento-design.md`

## Global Constraints

- Situações (enum, strings exatas): `SEM_CARDAPIO`, `FECHADA`, `AGUARDANDO`, `EM_ANDAMENTO`, `PENDENTE`; precedência nessa ordem.
- `FECHADA` = cardápio ativo e toda receita do cardápio tem medição em (data, refeição, escola 1). Parcial não é fechada.
- Hora local no fuso `ApplicationConfig.ZONA_OPERACIONAL` (`America/Sao_Paulo`) a partir do `Clock` injetado; janela `[início, fim)`.
- `GET /api/v1/fechamentos/dia?data=` e `GET /api/v1/fechamentos/pendencias?ate=&dias=` (`dias` 1..31, padrão 7); 400 fora disso / sem parâmetro obrigatório.
- Fechar fora da janela continua permitido; nada bloqueia o fechamento.
- Textos pt-BR com acento; testes com dados fictícios; comentários só para o não óbvio; sem commits (o Frank decide).
- Comandos: Maven `JAVA_HOME=$HOME/.jdks/corretto-25.0.4.1 $HOME/.m2/wrapper/dists/apache-maven-3.9.16-bin/5grr65jo27hi51sujmtcldfovl/apache-maven-3.9.16/bin/mvn -B -q test`; front `PATH=$HOME/.nvm/versions/node/v20.19.0/bin:$PATH CHROME_BIN=/tmp/claude-1975829934/-home-frank-serrano/dd761c4d-f72d-487e-b365-b552c902be76/scratchpad/chrome-ns.sh pnpm test --watch=false --browsers=ChromeHeadless` e `npx tsc -p tsconfig.app.json --noEmit`.
- A API do Frank roda em :8081 a partir desta sessão; o `ng serve` em :4200 — não derrubar nenhum dos dois.

## Review Focus

1. Hoje exatamente no `fim` da janela → `PENDENTE`, não `EM_ANDAMENTO` (teste na Task 1).
2. Cardápio cujos itens mudaram depois de medições (receita medida que saiu do cardápio) → conta só receitas do cardápio atual (teste na Task 1).
3. Refeição fechada antes do início da janela (data futura com medições) → `FECHADA` (teste na Task 1).
4. Troca de data no Fechamento enquanto a resposta do painel anterior ainda chega → a resposta velha não sobrescreve (teste na Task 3).
5. Parâmetro `refeicao` na rota que não está na grade → cai na regra padrão de seleção, sem erro (teste na Task 3).

---

### Task 1: API — situação do fechamento e endpoints

**Files:**
- Create: `api-merenda-escolar/src/main/java/br/com/frankcardoso/merenda/fechamento/domain/SituacaoFechamento.java` (enum)
- Create: `.../fechamento/application/PainelFechamentoService.java`
- Create: `.../fechamento/api/FechamentoController.java`, `RefeicaoDoDiaResponse.java`, `FechamentoPendenteResponse.java`
- Modify (se preciso, consultas por intervalo): `.../medicao/infrastructure/MedicaoSobraRepository.java`, `.../cardapio/infrastructure/CardapioRepository.java`, `.../fila/infrastructure/AuditoriaConsumoRepository.java`
- Test: `.../fechamento/domain/SituacaoFechamentoTest.java` ou `.../fechamento/application/PainelFechamentoServiceTest.java` (unidade com `Clock.fixed`), `.../fechamento/api/FechamentoHttpTest.java` (MockMvc, `@SpringBootTest` como os demais *HttpTest)

**Interfaces:**
- Consumes: `GradeRefeicoesService.listar(): List<TipoRefeicao>` (ordenada por `ordem`); `CardapioRepository.findByDataAndTipoRefeicaoIdAndAtivoTrue`; itens do cardápio = JSON em `Cardapio.getItensJson()` com `receitaId` por item (mesmo formato que o `DadosDemonstracaoLoader.ids(...)` lê); `MedicaoSobraRepository.findAllByDataAndTipoRefeicaoIdAndEscolaIdOrderByReceitaId(data, tipoRefeicaoId, 1)`; `AuditoriaConsumoRepository.countByDataOperacionalAndTipoRefeicaoIdAndResultado(data, id, AUTORIZADO)`.
- Produces:
  - `enum SituacaoFechamento { SEM_CARDAPIO, FECHADA, AGUARDANDO, EM_ANDAMENTO, PENDENTE }`
  - `static SituacaoFechamento SituacaoFechamento.de(boolean temCardapio, boolean completa, LocalDate data, LocalTime inicio, LocalTime fim, LocalDate hoje, LocalTime agora)` — regra pura.
  - `PainelFechamentoService.doDia(LocalDate data): List<RefeicaoDoDiaResponse>`; `PainelFechamentoService.pendencias(LocalDate ate, int dias): List<FechamentoPendenteResponse>`.
  - `record RefeicaoDoDiaResponse(UUID tipoRefeicaoId, String refeicao, int ordem, LocalTime inicio, LocalTime fim, SituacaoFechamento situacao, UUID cardapioId, int itensCardapio, int itensMedidos, long consumosAutorizados, Instant fechadoEm)`; JSON de `inicio`/`fim` como `"HH:mm"` (`@JsonFormat(pattern = "HH:mm")`).
  - `record FechamentoPendenteResponse(LocalDate data, UUID tipoRefeicaoId, String refeicao, int itensCardapio, int itensMedidos)`.

- [ ] **Step 1: Testes da regra pura** (`SituacaoFechamentoTest`), com janela 09:30–10:30 e hoje = 2026-03-10:
  - sem cardápio → `SEM_CARDAPIO` (mesmo com `completa=true`);
  - completa → `FECHADA` em data passada, hoje 10:00 e data futura (2026-03-11);
  - não completa: hoje 09:29 → `AGUARDANDO`; 09:30 → `EM_ANDAMENTO`; 10:29 → `EM_ANDAMENTO`; 10:30 → `PENDENTE`; data 2026-03-09 → `PENDENTE`; data 2026-03-11 → `AGUARDANDO`.
- [ ] **Step 2: Testes do serviço** (`PainelFechamentoServiceTest`, repositórios mockados, `Clock.fixed(2026-03-10T15:00:00Z)` = 12:00 local):
  - `doDiaListaTodasAsRefeicoesDaGradeNaOrdem` — 5 linhas na ordem da grade, inclusive refeição sem cardápio (`SEM_CARDAPIO`, `cardapioId` null, `itensCardapio` 0);
  - `parcialNaoEFechada` — 2 de 3 receitas medidas, janela encerrada → `PENDENTE`, `itensMedidos=2`, `fechadoEm` null;
  - `fechadaTrazOMaiorRegistradoEm` — 3 de 3 → `FECHADA`, `fechadoEm` = maior `registradoEm`;
  - `medicaoDeReceitaForaDoCardapioNaoConta` — medição de receita que não está no cardápio não soma em `itensMedidos` nem completa;
  - `pendenciasSoTrazPendentesNoIntervalo` — `ate`=2026-03-10, `dias`=2: inclui 2026-03-09 e 2026-03-10, só `PENDENTE`, ordenado por data e ordem; `EM_ANDAMENTO`/`AGUARDANDO`/`SEM_CARDAPIO`/`FECHADA` fora.
- [ ] **Step 3: Testes HTTP** (`FechamentoHttpTest`): `GET /api/v1/fechamentos/dia` sem `data` → 400; com `data` → 200 e 5 itens com `situacao` e `inicio` no formato `"09:30"`; `GET /api/v1/fechamentos/pendencias?ate=…&dias=0` → 400, `dias=32` → 400, sem `dias` → 200.
- [ ] **Step 4:** rodar → FAIL (classes inexistentes).
- [ ] **Step 5:** implementar enum, serviço (situação calculada só via `SituacaoFechamento.de`), controller (`@Validated` + `@Min(1) @Max(31)` em `dias`, padrão 7; seguir o tratamento de 400 já existente no `RestExceptionHandler`).
- [ ] **Step 6:** `$MVN test` → BUILD SUCCESS (base 202).

---

### Task 2: Demonstração — sem fechamento de hoje

**Files:**
- Modify: `api-merenda-escolar/src/main/java/br/com/frankcardoso/merenda/shared/config/DadosDemonstracaoLoader.java` (em `garantirMedicoes(LocalDate hoje, …)`: pular `hoje` para toda refeição; remover a exceção do Almoço e o comentário dela)
- Modify: testes de demonstração que afirmam "só o Almoço tem fechamento hoje" (procurar em `src/test/java/.../shared/config/`)
- Modify: `api-merenda-escolar/README.md` (seção Dados de demonstração e roteiro: hoje nenhum fechamento vem pronto; às 12:00 o painel mostra Café e Lanche da manhã pendentes), `api-merenda-escolar/DECISOES-TECNICAS.md` (nova subseção curta na seção 9: "Painel do dia no fechamento" — situações, por que parcial não fecha, fim do fechamento pré-gerado do Almoço)

**Interfaces:** Consumes Task 1 (`PainelFechamentoService.doDia`) só no teste.

- [ ] **Step 1: Teste** em `DadosDemonstracaoTurmasTest` (ou o teste de demonstração que já cobre medições): `nenhumaRefeicaoDeHojeVemFechada` — depois do boot, `medicoes` de hoje vazias para todas as refeições, e dias úteis anteriores com cardápio continuam com medição; trocar a asserção antiga do Almoço.
- [ ] **Step 2:** FAIL → implementar → `$MVN test` verde.
- [ ] **Step 3:** atualizar README e DECISOES (sem acento no DECISOES, como o arquivo).

---

### Task 3: Front — painel do dia no Fechamento

**Files:**
- Create: `front-merenda-escolar/src/app/features/fechamento/models/painel.models.ts` (`SituacaoFechamento`, `RefeicaoDoDia`, `FechamentoPendente`)
- Create: `front-merenda-escolar/src/app/features/fechamento/data-access/fechamento-painel-api.service.ts` (+ `.spec.ts`)
- Create: `front-merenda-escolar/src/app/features/fechamento/components/painel-dia/painel-dia.component.{ts,html,spec.ts}`
- Modify: `features/fechamento/pages/fechamento-page/fechamento-page.component.{ts,html,spec.ts}`

**Interfaces:**
- Consumes: API da Task 1.
- Produces:
  - `type SituacaoFechamento = 'SEM_CARDAPIO' | 'FECHADA' | 'AGUARDANDO' | 'EM_ANDAMENTO' | 'PENDENTE'`; `interface RefeicaoDoDia { tipoRefeicaoId: string; refeicao: string; ordem: number; inicio: string; fim: string; situacao: SituacaoFechamento; cardapioId: string | null; itensCardapio: number; itensMedidos: number; consumosAutorizados: number; fechadoEm: string | null }`; `interface FechamentoPendente { data: string; tipoRefeicaoId: string; refeicao: string; itensCardapio: number; itensMedidos: number }`.
  - `FechamentoPainelApiService.doDia(data: string): Observable<RefeicaoDoDia[]>`; `pendencias(ate: string, dias = 7): Observable<FechamentoPendente[]>`.
  - `PainelDiaComponent` (`app-painel-dia`): `refeicoes = input<RefeicaoDoDia[]>([])`, `selecionada = input<string>('')`, `escolher = output<string>()`.
  - Função pura exportada `escolherRefeicaoInicial(refeicoes: RefeicaoDoDia[], pedida?: string | null): string` (pedida válida → ela; senão 1ª `PENDENTE`; senão `EM_ANDAMENTO`; senão a 1ª; lista vazia → `''`).

- [ ] **Step 1: Testes que falham.**
  - service spec: `GET …/fechamentos/dia?data=2026-03-10`; `GET …/fechamentos/pendencias?ate=2026-03-10&dias=7`.
  - `escolherRefeicaoInicial`: pendente antes de em andamento; em andamento quando não há pendente; primeira quando nenhuma; pedida válida vence; pedida fora da lista é ignorada.
  - painel spec: renderiza um cartão por refeição com o selo (`Pendente`, `Em andamento`, `Fechada às 12:05` a partir de `fechadoEm` em hora local, `Aguardando`, `Sem cardápio`), "36 alunos na fila" e "0/5 itens"; clique em cartão com cardápio emite o id; cartão `SEM_CARDAPIO` desabilitado; o cartão selecionado tem `aria-current="true"`.
  - página spec: ao abrir sem parâmetros, seleciona a 1ª pendente e chama `buscarConsolidacao(DATA, idPendente)`; com `?data=2026-03-09&refeicao=id-almoco` usa os dois; clique num cartão carrega aquela refeição; depois de fechar, `doDia` é chamado de novo; troca de data com resposta antiga chegando depois não sobrescreve o painel (Review Focus 4).
- [ ] **Step 2:** FAIL → implementar. Selos: `PENDENTE` âmbar, `EM_ANDAMENTO` azul, `FECHADA` verde, demais cinza (Tailwind, como o resto da página). Título da faixa: `Refeições do dia`. Parâmetros de rota via `ActivatedRoute.queryParamMap` (snapshot ao abrir).
- [ ] **Step 3:** testes do front verdes + `tsc` sem erro.

---

### Task 4: Front — aviso de pendências no Dashboard + README do front

**Files:**
- Create: `front-merenda-escolar/src/app/features/dashboard/components/pendencias-fechamento/pendencias-fechamento.component.{ts,html,spec.ts}`
- Modify: `features/dashboard/pages/dashboard-page/dashboard-page.component.{ts,html,spec.ts}`
- Modify: `front-merenda-escolar/README.md` (Fechamento com painel do dia; aviso no Dashboard; rotas novas da API)

**Interfaces:** Consumes `FechamentoPainelApiService.pendencias` e `FechamentoPendente` (Task 3).

- [ ] **Step 1: Testes.**
  - componente: `pendencias = input<FechamentoPendente[]>([])`; vazio → não renderiza nada; com 2 → texto `2 refeições sem fechamento nos últimos 7 dias` (1 → `1 refeição sem fechamento nos últimos 7 dias`), itens `09/03 • Café da manhã` com `routerLink` `/fechamento` e `queryParams` `{ data: '2026-03-09', refeicao: 'id-cafe' }`.
  - página: consulta `pendencias(dataReferencia, 7)` ao carregar e ao trocar a data; erro na consulta não mostra notificação de erro nem quebra os indicadores (aviso some).
- [ ] **Step 2:** FAIL → implementar (aviso só na aba Indicadores, acima dos cartões; estilo âmbar como o `aviso` do NotificacaoService).
- [ ] **Step 3:** testes do front verdes + `tsc`; README do front atualizado.
