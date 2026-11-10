# Grade de refeições visível e editável Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tornar visível a que refeições cada turma tem direito, explicar o bloqueio por turma na fila e permitir criar/editar a grade de refeições.

**Architecture:** A API ganha POST/PUT em `/api/v1/tipos-refeicao`, validados pelo domínio `GradeRefeicoes` (sem sobreposição por turno), e a fila passa a devolver a refeição da janela e os turnos envolvidos no bloqueio por turma. O front ganha utilitários de turno/grade compartilhados, coluna e dica de direito nas Turmas, texto novo na fila e uma tela "Grade de refeições".

**Tech Stack:** Spring Boot 3.5.16 / Java 25 / H2 / JUnit 5 + AssertJ + MockMvc; Angular 20 (signals, standalone) + Karma/Jasmine.

**Spec:** `api-merenda-escolar/docs/superpowers/specs/2026-11-10-grade-refeicoes-visivel-design.md`

## Global Constraints

- Turnos (strings exatas): `MANHA`, `TARDE`, `NOITE`, `INTEGRAL`; rótulos "Manhã", "Tarde", "Noite", "Integral".
- Janela `[início, fim)`, horários `"HH:mm"` no JSON de entrada; `ordem` derivada do `inicio` depois de salvar (1..n).
- Grade nunca inválida: sobreposição para o mesmo turno → 422 com a mensagem de `GradeRefeicoesInvalidaException`; `inicio >= fim` → 422 "O início precisa ser antes do fim."; nome repetido (sem diferenciar maiúsculas) → 409; id inexistente → 404. Nunca 500.
- Sem exclusão de refeição; `turnos` vazio permitido. Sem migration nova.
- Histórico intacto: nada que já foi gravado é reescrito ao editar a grade.
- Textos pt-BR com acento; testes com dados fictícios; comentários só para o não óbvio; sem commits.
- Comandos: Maven `JAVA_HOME=$HOME/.jdks/corretto-25.0.4.1 $HOME/.m2/wrapper/dists/apache-maven-3.9.16-bin/5grr65jo27hi51sujmtcldfovl/apache-maven-3.9.16/bin/mvn -B -q test` (base 216); front `PATH=$HOME/.nvm/versions/node/v20.19.0/bin:$PATH CHROME_BIN=/tmp/claude-1975829934/-home-frank-serrano/dd761c4d-f72d-487e-b365-b552c902be76/scratchpad/chrome-ns.sh pnpm test --watch=false --browsers=ChromeHeadless` (base 137) e `npx tsc -p tsconfig.app.json --noEmit`.
- A API do Frank roda em :8081 a partir desta sessão (base `data/merenda`) e o `ng serve` em :4200 — não derrubar, não tocar em `data/`.

## Review Focus

1. Editar uma refeição sem mudar a janela não pode acusar sobreposição com ela mesma (Task 1).
2. Grade com a refeição editada inválida não pode ficar gravada pela metade — a validação acontece antes do `save` (Task 1).
3. Bloqueio por turma com duas refeições na janela para turnos diferentes → a de menor ordem é a informada (Task 2).
4. Turma com turno sem nenhuma refeição → "Nenhuma refeição", sem erro (Task 3).
5. Salvar a grade com o mesmo nome de outra refeição em maiúsculas/minúsculas diferentes → 409 com mensagem (Task 1 e Task 4).

---

### Task 1: API — criar e editar refeição da grade

**Files:**
- Create: `.../refeicao/api/TipoRefeicaoRequest.java`
- Modify: `.../refeicao/api/TipoRefeicaoController.java` (POST, PUT), `.../refeicao/application/GradeRefeicoesService.java` (escrita transacional à parte das leituras), `.../refeicao/domain/TipoRefeicao.java` (método de atualização e de ordem), `.../refeicao/infrastructure/TipoRefeicaoRepository.java` (busca por nome sem diferenciar maiúsculas, se preciso), `.../shared/web/RestExceptionHandler.java` (422 para grade inválida e início ≥ fim, 409 para nome repetido — seguir o padrão `problema(...)`)
- Create: exceções de aplicação no padrão do projeto (ex.: `NomeDeRefeicaoDuplicadoException`, `JanelaDeRefeicaoInvalidaException`)
- Modify: `api-merenda-escolar/README.md` (endpoints novos; a grade é editável pela tela "Grade de refeições"; mudanças valem daqui para frente)
- Test: `.../refeicao/application/GradeRefeicoesServiceTest.java` (unidade ou `@DataJpaTest`), `.../refeicao/api/TipoRefeicaoEscritaHttpTest.java` (MockMvc, `@SpringBootTest` como os demais *HttpTest)

**Interfaces:**
- Produces:
  - `record TipoRefeicaoRequest(@NotBlank @Size(max = 60) String nome, @NotNull @JsonFormat(pattern = "HH:mm") LocalTime inicio, @NotNull @JsonFormat(pattern = "HH:mm") LocalTime fim, @NotNull Set<Turno> turnos)`
  - `GradeRefeicoesService.criar(TipoRefeicaoRequest): TipoRefeicao`; `GradeRefeicoesService.atualizar(UUID id, TipoRefeicaoRequest): TipoRefeicao`
  - `POST /api/v1/tipos-refeicao` → 201 `TipoRefeicaoResponse`; `PUT /api/v1/tipos-refeicao/{id}` → 200 `TipoRefeicaoResponse`

- [ ] **Step 1: Testes.**
  - `criaRefeicaoERenumeraPeloInicio`: criar "Lanche extra" 14:00–14:30 `[TARDE]` → `ordem` dele = 4, Lanche da tarde passa a 5, Lanche da noite a 6.
  - `editaJanelaSemAcusarSobreposicaoConsigoMesma`: PUT do Almoço para 11:45–13:15 com os mesmos turnos → 200 (Review Focus 1).
  - `sobreposicaoParaOMesmoTurnoRetorna422ENaoGrava`: PUT do Almoço para 07:30–08:30 `[INTEGRAL]` (bate com o Café) → 422 com "se sobrepõem para o mesmo turno"; GET mostra o Almoço inalterado (Review Focus 2).
  - `inicioDepoisDoFimRetorna422` → mensagem "O início precisa ser antes do fim.".
  - `nomeRepetidoSemDiferenciarMaiusculasRetorna409`: POST "almoço" → 409 (Review Focus 5).
  - `idInexistenteRetorna404`; `turnosVaziosSaoAceitos` (PUT do Lanche da noite com `[]` → 200).
  - `editarNomeNaoReescreveHistorico`: auditoria/fechamento já gravados com o id da refeição continuam com o id; `auditoria_consumo.refeicao_nome` carimbado continua o antigo.
- [ ] **Step 2:** FAIL → implementar (validar com `GradeRefeicoes.de(gradeResultante)` em memória antes de gravar; renumerar `ordem` por `inicio` depois). Escrita com `@Transactional` própria (a classe é `readOnly`).
- [ ] **Step 3:** `$MVN test` verde; README atualizado.

---

### Task 2: API — fila explica o bloqueio por turma

**Files:**
- Modify: `.../refeicao/domain/ResolucaoRefeicao.java`, `.../refeicao/domain/GradeRefeicoes.java` (`resolver`), `.../fila/api/ValidarConsumoResponse.java`, `.../fila/application/ValidacaoConsumoService.java` (~:134-135, onde `bloqueio(aluno, null, null, motivo)` é montado)
- Test: `.../refeicao/domain/GradeRefeicoesTest.java` (ou o teste de domínio da grade existente), `.../fila/application/ValidacaoConsumoServiceTest.java`, e o teste de integração da fila por refeição (`FilaPorRefeicaoIntegracaoTest`)

**Interfaces:**
- Produces:
  - `ResolucaoRefeicao` ganha `TipoRefeicao refeicaoNaJanela` (preenchida no bloqueio por turma; nula nos demais); fábrica `ResolucaoRefeicao.naoPrevista(TipoRefeicao naJanela)`.
  - `ValidarConsumoResponse` ganha, no fim, `String turnoDaTurma, List<String> turnosDaRefeicao` (nulos quando não se aplicam). Em `REFEICAO_NAO_PREVISTA_PARA_TURMA`: `tipoRefeicaoId`/`refeicao` = refeição na janela, `alunoId`/`alunoNome` preenchidos como hoje, `turnoDaTurma` = turno da turma, `turnosDaRefeicao` = turnos da refeição em ordem `MANHA, TARDE, NOITE, INTEGRAL`.

- [ ] **Step 1: Testes.**
  - domínio: às 12:00 com turno `MANHA` → bloqueada, `refeicaoNaJanela` = Almoço; duas refeições na janela para turnos diferentes (fixture com janelas sobrepostas para turnos distintos) e turno sem direito a nenhuma → a de menor `ordem` (Review Focus 3).
  - serviço/integração: aluno de turma `MANHA` às 12:00 → `BLOQUEADO`, `motivo` `REFEICAO_NAO_PREVISTA_PARA_TURMA`, `refeicao` "Almoço", `turnoDaTurma` "MANHA", `turnosDaRefeicao` `["INTEGRAL"]`; autorizado e demais bloqueios com os dois campos nulos.
- [ ] **Step 2:** FAIL → implementar → `$MVN test` verde.

---

### Task 3: Front — direito visível nas Turmas e bloqueio explicado na fila

**Files:**
- Create: `front-merenda-escolar/src/app/shared/refeicao/turnos.ts` (+ `turnos.spec.ts`)
- Modify: `shared/refeicao/tipo-refeicao.models.ts` (se precisar tipar `turnos`)
- Modify: `features/cadastro/pages/turma-page/turma-page.component.{ts,html,spec.ts}`
- Modify: `features/fila/models/fila.models.ts`, `features/fila/pages/fila-page/fila-page.component.{ts,spec.ts}` (o detalhe vem hoje de `traduzirMotivo(resultado.motivo)` ~:82)

**Interfaces:**
- Consumes: `TipoRefeicaoApiService.listar()`; resposta da fila da Task 2.
- Produces:
  - `const ROTULO_TURNO: Record<string, string>` (`MANHA`→"Manhã", `TARDE`→"Tarde", `NOITE`→"Noite", `INTEGRAL`→"Integral"); `function rotuloTurno(turno: string): string` (desconhecido → o próprio valor).
  - `function refeicoesDoTurno(grade: TipoRefeicao[], turno: string): TipoRefeicao[]` (ordem da grade).
  - `function explicarBloqueioPorTurma(refeicao: string, turnosDaRefeicao: string[], turnoDaTurma: string): string` → `"Almoço é para turmas Integral; a turma do aluno é Manhã."`; vários turnos unidos por " e " antes do último e ", " entre os demais (`"Lanche da manhã é para turmas Manhã e Integral; …"`); lista vazia → `"{refeicao} não está liberada para nenhum turno; a turma do aluno é {turno}."`.
  - `ValidarConsumoResponse` (front) ganha `turnoDaTurma: string | null; turnosDaRefeicao: string[] | null`.

- [ ] **Step 1: Testes.**
  - `turnos.spec`: rótulos; `refeicoesDoTurno` (INTEGRAL → 4 na ordem; NOITE → 1; turno sem refeição → `[]`); as três formas de `explicarBloqueioPorTurma`.
  - `turma-page.spec`: coluna "Refeições" com selos (`7A` INTEGRAL → "Café da manhã", "Lanche da manhã", "Almoço", "Lanche da tarde"); turno sem refeição → "Nenhuma refeição" (Review Focus 4); no formulário, trocar o turno para `TARDE` mostra "Esta turma terá direito a: Lanche da tarde".
  - `fila-page.spec`: bloqueio por turma com os campos → detalhe = texto de `explicarBloqueioPorTurma`; sem os campos → texto atual de `traduzirMotivo`.
- [ ] **Step 2:** FAIL → implementar (Turmas carrega a grade via `TipoRefeicaoApiService`; falha ao carregar a grade não derruba a tela, só esconde coluna/dica).
- [ ] **Step 3:** testes verdes + `tsc`.

---

### Task 4: Front — tela "Grade de refeições"

**Files:**
- Create: `front-merenda-escolar/src/app/features/grade-refeicoes/pages/grade-refeicoes-page/grade-refeicoes-page.component.{ts,html,spec.ts}` (formulário pode ser componente próprio em `components/` se ficar grande)
- Modify: `shared/refeicao/tipo-refeicao-api.service.{ts,spec.ts}` (`criar`, `atualizar`), `shared/refeicao/tipo-refeicao.models.ts` (`TipoRefeicaoRequest`)
- Modify: `src/app/app.routes.ts` (rota `grade-refeicoes`), `src/app/app.html` (item "Refeições" no menu, depois de "Cardápios")
- Modify: `front-merenda-escolar/README.md` (tela nova, coluna nas Turmas, bloqueio explicado)

**Interfaces:**
- Consumes: API da Task 1; `rotuloTurno` da Task 3.
- Produces: `interface TipoRefeicaoRequest { nome: string; inicio: string; fim: string; turnos: string[] }`; `TipoRefeicaoApiService.criar(r): Observable<TipoRefeicao>` (POST), `atualizar(id, r): Observable<TipoRefeicao>` (PUT).

- [ ] **Step 1: Testes.**
  - service spec: POST e PUT nas URLs certas com o corpo.
  - página: lista a grade (nome, `07:00–08:00`, selos de turno com rótulo); "Nova refeição" abre o formulário vazio; "Editar" preenche nome, horários e caixas; salvar chama `criar`/`atualizar` com `turnos` marcados e recarrega a lista com notificação de sucesso; 422/409 mostram a mensagem `detail` da API em `NotificacaoService.erro` e mantêm o formulário aberto; texto de apoio "A grade define quem tem direito a cada refeição pelo turno da turma. Mudanças valem daqui para frente." presente.
- [ ] **Step 2:** FAIL → implementar (inputs `type="time"`; botão salvar desabilitado sem nome ou com horário vazio).
- [ ] **Step 3:** testes verdes + `tsc`; README do front atualizado.
