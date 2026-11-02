# Turma e aluno Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transformar `turma` de string dentro de `Aluno` em entidade própria, ligada ao aluno por enturmação com vigência, com CRUD de aluno e turma e a turma carimbada no consumo.

**Architecture:** `Turma` e `Enturmacao` nascem no pacote `turma/` do backend. A fila, ao autorizar, resolve a enturmação vigente na data e **carimba** turma id e nome em `auditoria_consumo` — o agrupamento do dashboard passa a ler o carimbo, não `aluno.turma`. O contrato Java↔Python sobe para `schemaVersion` 4 e aceita turma sem id (balde "Sem turma"). O front ganha uma feature `cadastro` com telas de aluno, turma e transferência.

**Tech Stack:** Java 25 / Spring Boot 3.5 / H2 / Flyway / JUnit 5; Python 3.12 / FastAPI / Pydantic / pytest; Angular 20 standalone / Tailwind / Karma.

**Spec:** `docs/superpowers/specs/2026-10-19-turma-aluno-design.md`

## Global Constraints

- O banco local é descartável: apagar `data/merenda.mv.db` antes do primeiro boot. Não há backfill de dados reais.
- A migration nova é a `V10`. V1–V9 ficam intactas.
- Java e Python andam em lockstep: `schemaVersion` sobe para `"4"` nos dois no mesmo commit lógico.
- O Python não lê domínio: tudo que ele precisa chega no pedido.
- Números do dashboard nunca vêm da resposta do LLM.
- Uma feature do Angular não importa arquivo interno de outra feature.
- Adesão por turma continua `NAO_AVALIAVEL`. Nenhuma task calcula adesão.
- `porTurma` lista só turmas com atendimento no dia/turno.
- Ids de entidade são `UUID`, como `Aluno` já faz.

## Review Focus

1. **Enturmação com `dataFim` anterior a `dataInicio`** — deve ser rejeitada no cadastro, não gravada e descoberta depois na consulta. Teste na Task 1.
2. **Transferência retroativa para antes do início do vínculo vigente** — deve ser rejeitada; aceitar criaria vínculo com vigência negativa. Teste na Task 6.
3. **Consumo de aluno sem enturmação vigente** — o total de `alunosUnicos` do dashboard tem de continuar igual ao total real; o balde "Sem turma" existe exatamente para isso. Teste na Task 3.
4. **Exclusão de turma que já tem enturmação ou consumo carimbado** — deve ser recusada; o carimbo do histórico não pode apontar para turma inexistente. Teste na Task 6.
5. **`indicadores_json` persistido com `schemaVersion` 2 ou 3 aberto no front** — relatório antigo não tem `turmaId`; a tela deve renderizar sem quebrar. Teste na Task 9.

---

### Task 1: Entidades `Turma` e `Enturmacao`

**Files:**
- Create: `src/main/java/br/com/frankcardoso/merenda/turma/domain/Turma.java`
- Create: `src/main/java/br/com/frankcardoso/merenda/turma/domain/Enturmacao.java`
- Create: `src/main/java/br/com/frankcardoso/merenda/turma/infrastructure/TurmaRepository.java`
- Create: `src/main/java/br/com/frankcardoso/merenda/turma/infrastructure/EnturmacaoRepository.java`
- Create: `src/main/resources/db/migration/V10__turma_e_enturmacao.sql`
- Modify: `src/main/java/br/com/frankcardoso/merenda/aluno/domain/Aluno.java` — remover o campo `turma` e o parâmetro correspondente do construtor
- Modify: `src/main/java/br/com/frankcardoso/merenda/shared/config/DadosDemonstracaoLoader.java:157-165` — acompanhar o construtor de `Aluno`
- Test: `src/test/java/br/com/frankcardoso/merenda/turma/domain/EnturmacaoTest.java`

**Interfaces:**
- Consumes: `Aluno` (`aluno/domain/Aluno.java`), enum `Turno` (`fila/domain/Turno.java`).
- Produces:
  - `Turma(UUID id, String codigo, String descricao, Turno turno, Integer quantidadeMaximaAlunos, String codigoOrigem, Instant criadoEm)` com getters.
  - `Enturmacao(UUID id, Aluno aluno, Turma turma, LocalDate dataInicio, LocalDate dataFim, String codigoOrigem)` com getters e `boolean vigenteEm(LocalDate data)`.
  - `TurmaRepository extends JpaRepository<Turma, UUID>`, com `Optional<Turma> findByCodigo(String codigo)`.
  - `EnturmacaoRepository extends JpaRepository<Enturmacao, UUID>`, com:
    - `Optional<Enturmacao> vigenteEm(UUID alunoId, LocalDate data)` — `dataInicio <= data` e (`dataFim` nulo ou `dataFim >= data`).
    - `List<Enturmacao> findByAlunoIdOrderByDataInicioDesc(UUID alunoId)`.
    - `boolean existsByTurmaId(UUID turmaId)`.

A `V10` cria `turma` e `enturmacao`, e remove a coluna `turma` de `aluno`. `Aluno` perde o getter `getTurma()`: quem precisar da turma passa a consultar a enturmação vigente. `enturmacao` leva FK para `aluno` e `turma`, índice em `(aluno_id, data_inicio)` e `CHECK (data_fim IS NULL OR data_fim >= data_inicio)`.

- [ ] **Step 1: Write the failing test**

```java
class EnturmacaoTest {
    @Test void vigenteEmDentroDoIntervaloFechado() // inicio 2026-02-01, fim 2026-07-31 → vigente em 2026-07-31
    @Test void vigenteEmComDataFimNula() // inicio 2026-08-01, fim null → vigente em 2026-12-31
    @Test void naoVigenteAntesDoInicio() // inicio 2026-08-01 → não vigente em 2026-07-31
    @Test void dataFimAnteriorAoInicioRejeitada() // construtor lança IllegalArgumentException
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=EnturmacaoTest`
Expected: FAIL — classe `Enturmacao` não existe.

- [ ] **Step 3: Implement `Turma`, `Enturmacao`, os dois repositórios e a `V10`**

`Enturmacao` valida no construtor que `dataFim` não é anterior a `dataInicio`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=EnturmacaoTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/frankcardoso/merenda/turma src/main/resources/db/migration/V10__turma_e_enturmacao.sql src/test/java/br/com/frankcardoso/merenda/turma
git commit -m "feat(turma): entidade turma e enturmacao com vigencia"
```

---

### Task 2: Carimbo da turma no consumo

**Files:**
- Modify: `src/main/java/br/com/frankcardoso/merenda/fila/domain/AuditoriaConsumo.java`
- Modify: `src/main/java/br/com/frankcardoso/merenda/fila/application/ValidacaoConsumoService.java`
- Modify: `src/main/resources/db/migration/V10__turma_e_enturmacao.sql`
- Test: `src/test/java/br/com/frankcardoso/merenda/fila/application/ValidacaoConsumoServiceTest.java`

**Interfaces:**
- Consumes: `EnturmacaoRepository.vigenteEm(UUID, LocalDate)` da Task 1.
- Produces: `AuditoriaConsumo` com dois campos novos — `turmaId` (`UUID`, nulo permitido) e `turmaNome` (`String`, 80). O construtor ganha os dois no fim da lista de parâmetros. Constante pública `AuditoriaConsumo.SEM_TURMA = "Sem turma"`.

A `V10` acrescenta `turma_id UUID` e `turma_nome VARCHAR(80)` a `auditoria_consumo`.

- [ ] **Step 1: Write the failing test**

Acrescentar a `ValidacaoConsumoServiceTest`:

```java
@Test void carimbaTurmaVigenteNaDataDoConsumo()   // enturmação 8A vigente → auditoria.turmaNome == "8A"
@Test void carimbaSemTurmaQuandoNaoHaEnturmacaoVigente() // → turmaId null, turmaNome == "Sem turma"
@Test void autorizaMesmoSemEnturmacaoVigente()    // resultado == AUTORIZADO
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=ValidacaoConsumoServiceTest`
Expected: FAIL — `turmaNome` não existe.

- [ ] **Step 3: Implement o carimbo**

`ValidacaoConsumoService` recebe `EnturmacaoRepository` no construtor e resolve a enturmação vigente em `data` antes de montar a auditoria, tanto no caminho autorizado quanto no bloqueado.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=ValidacaoConsumoServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/frankcardoso/merenda/fila src/main/resources/db/migration/V10__turma_e_enturmacao.sql src/test/java/br/com/frankcardoso/merenda/fila
git commit -m "feat(fila): carimbar turma vigente no consumo"
```

---

### Task 3: Agrupamento por turma carimbada

**Files:**
- Modify: `src/main/java/br/com/frankcardoso/merenda/fila/infrastructure/AuditoriaConsumoRepository.java`
- Modify: `src/main/java/br/com/frankcardoso/merenda/analytics/application/port/IndicadoresLogisticosInput.java:47`
- Modify: `src/main/java/br/com/frankcardoso/merenda/gestao/application/IndicadoresDadosService.java`
- Test: `src/test/java/br/com/frankcardoso/merenda/gestao/application/IndicadoresDadosServiceTest.java`

**Interfaces:**
- Consumes: o carimbo da Task 2.
- Produces:
  - `AtendimentoTurma` ganha `UUID getTurmaId()`; `getTurma()` passa a devolver `turmaNome`. O JPQL agrupa por `a.turmaId, a.turmaNome`, não mais por `a.aluno.turma`.
  - `ContagemTurma(UUID turmaId, String turma, long consumosRegistrados, long alunosUnicos)` — `turmaId` nulo no balde "Sem turma".

- [ ] **Step 1: Write the failing test**

Acrescentar a `IndicadoresDadosServiceTest`:

```java
@Test void agrupaPelaTurmaCarimbadaNaoPelaTurmaAtual()
// aluno consome em agosto carimbado 7A, hoje está na 8A → a contagem de agosto sai em 7A

@Test void consumoSemTurmaEntraNoBaldeESomaNoTotal()
// 3 alunos em turmas + 2 alunos sem turma → alunosUnicos do input == 5
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=IndicadoresDadosServiceTest`
Expected: FAIL

- [ ] **Step 3: Implement o novo agrupamento**

Trocar a projeção do JPQL e propagar `turmaId` até `ContagemTurma`. A soma de `alunosUnicos` em `IndicadoresDadosService` não muda de forma — ela só passa a incluir o balde.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=IndicadoresDadosServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/frankcardoso/merenda/fila/infrastructure src/main/java/br/com/frankcardoso/merenda/analytics src/main/java/br/com/frankcardoso/merenda/gestao src/test
git commit -m "feat(gestao): agrupar atendimentos pela turma carimbada"
```

---

### Task 4: Contrato do Python — turma com id, balde e `schemaVersion` 4

**Files:**
- Modify: `app/domain/indicadores_logisticos.py:15` (`ContagemTurma`), `:171` (`TurmaIndicadores.motivo`), `:237` (`schema_version`)
- Test: `tests/domain/test_indicadores_logisticos.py`

**Interfaces:**
- Produces: `ContagemTurma` com `turma_id: str | None = None` e `turma: str = Field(min_length=1, max_length=80)`. O `min_length` fica: quem não tem turma manda o rótulo `"Sem turma"` com `turma_id` nulo. `IndicadoresResponse.schema_version = "4"`.

Repo: `analytics-merenda-escolar`.

- [ ] **Step 1: Write the failing test**

```python
def test_contagem_turma_aceita_turma_id_nulo()        # turma="Sem turma", turma_id=None → válido
def test_contagem_turma_propaga_turma_id_na_resposta() # por_turma[0].turma_id preservado
def test_schema_version_e_4()
def test_motivo_por_turma_nao_cita_turma_atual_do_cadastro()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./.venv/bin/python -m pytest tests/domain/test_indicadores_logisticos.py -v`
Expected: FAIL

- [ ] **Step 3: Implement**

O motivo novo de `TurmaIndicadores` diz que falta presença elegível e que a turma é a vigente na data do consumo. O aviso de `calcular_indicadores` sobre adesão por turma acompanha.

- [ ] **Step 4: Run tests**

Run: `./.venv/bin/python -m pytest -v && ./.venv/bin/python -m ruff check .`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/domain/indicadores_logisticos.py tests/domain/test_indicadores_logisticos.py
git commit -m "feat(analytics): turma com id e schema 4"
```

---

### Task 5: Adapter Java aceita `schemaVersion` 4

**Files:**
- Modify: `src/main/java/br/com/frankcardoso/merenda/analytics/infrastructure/python/PythonIndicadoresLogisticosAdapter.java:39`
- Modify: `src/main/java/br/com/frankcardoso/merenda/analytics/application/port/IndicadoresLogisticos.java:48`
- Test: `src/test/java/br/com/frankcardoso/merenda/analytics/infrastructure/python/PythonIndicadoresLogisticosAdapterTest.java`

**Interfaces:**
- Consumes: resposta do Python da Task 4.
- Produces: `IndicadoresLogisticos` com `TurmaIndicadores` carregando `turmaId`.

- [ ] **Step 1: Write the failing test**

```java
@Test void aceitaRespostaSchema4()
@Test void rejeitaRespostaSchema3()   // status INDISPONIVEL
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=PythonIndicadoresLogisticosAdapterTest`
Expected: FAIL

- [ ] **Step 3: Implement** — trocar `"3"` por `"4"` nos dois pontos e incluir `turmaId` no record de turma.

- [ ] **Step 4: Run test** — Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/frankcardoso/merenda/analytics src/test/java/br/com/frankcardoso/merenda/analytics
git commit -m "feat(analytics): adapter em schema 4"
```

---

### Task 6: CRUD de aluno e turma, e transferência

**Files:**
- Create: `src/main/java/br/com/frankcardoso/merenda/aluno/api/AlunoController.java`
- Create: `src/main/java/br/com/frankcardoso/merenda/aluno/application/AlunoService.java`
- Create: `src/main/java/br/com/frankcardoso/merenda/turma/api/TurmaController.java`
- Create: `src/main/java/br/com/frankcardoso/merenda/turma/application/TurmaService.java`
- Create: `src/main/java/br/com/frankcardoso/merenda/turma/application/EnturmacaoService.java`
- Test: `src/test/java/br/com/frankcardoso/merenda/turma/application/EnturmacaoServiceTest.java`
- Test: `src/test/java/br/com/frankcardoso/merenda/turma/application/TurmaServiceTest.java`

**Interfaces:**
- Consumes: repositórios da Task 1.
- Produces:
  - `GET|POST|PUT /api/v1/alunos`, `GET /api/v1/alunos/{id}` com a turma vigente e o histórico de vínculos.
  - `GET|POST|PUT|DELETE /api/v1/turmas`.
  - `POST /api/v1/alunos/{id}/transferencias` com corpo `{turmaId, dataMudanca}`.
  - `EnturmacaoService.transferir(UUID alunoId, UUID turmaId, LocalDate dataMudanca)` — fecha o vínculo vigente com `dataFim = dataMudanca.minusDays(1)` e abre o novo em `dataMudanca`.
  - `EnturmacaoService.enturmar(UUID alunoId, UUID turmaId, LocalDate dataInicio)` — primeiro vínculo.

- [ ] **Step 1: Write the failing test**

```java
class EnturmacaoServiceTest {
    @Test void transferirFechaVinculoAnteriorNoDiaAnterior()
    @Test void transferirRejeitaDataAnteriorAoInicioDoVinculoVigente() // IllegalArgumentException
    @Test void enturmarRejeitaSobreposicaoDeVigencia()
}
class TurmaServiceTest {
    @Test void excluirRejeitaTurmaComEnturmacao()
    @Test void excluirRejeitaTurmaComConsumoCarimbado()
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest='EnturmacaoServiceTest,TurmaServiceTest'`
Expected: FAIL

- [ ] **Step 3: Implement serviços e controllers**

`TurmaService.excluir` consulta `EnturmacaoRepository.existsByTurmaId` e um `existsByTurmaId` novo em `AuditoriaConsumoRepository`.

- [ ] **Step 4: Run the full suite**

Run: `./mvnw test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/frankcardoso/merenda/aluno src/main/java/br/com/frankcardoso/merenda/turma src/test/java/br/com/frankcardoso/merenda/turma
git commit -m "feat(cadastro): crud de aluno e turma com transferencia"
```

---

### Task 7: Loader de demonstração com turmas, enturmações e uma transferência

**Files:**
- Modify: `src/main/java/br/com/frankcardoso/merenda/shared/config/DadosDemonstracaoLoader.java:151-172`
- Test: `src/test/java/br/com/frankcardoso/merenda/shared/config/DadosDemonstracaoTurmasTest.java`

**Interfaces:**
- Consumes: Tasks 1, 2 e 6.
- Produces: nenhuma assinatura nova.

As 9 turmas sintéticas `"%dA"` (1A a 9A) viram `Turma` com turno `MANHA`. Cada aluno ganha enturmação começando antes do início do histórico sintético, para nenhum consumo gerado cair no balde. Um aluno troca de turma no meio do intervalo, para a tela de transferência ter o que demonstrar.

- [ ] **Step 1: Write the failing test**

```java
@Test void criaNoveTurmasComEnturmacaoCobrindoTodoOHistorico()
@Test void umAlunoTrocaDeTurmaNoMeioDoIntervalo() // 2 enturmações, a primeira com dataFim
@Test void nenhumConsumoGeradoCaiNoBaldeSemTurma()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=DadosDemonstracaoTurmasTest`
Expected: FAIL

- [ ] **Step 3: Implement** — `garantirAlunos` passa a garantir turma e enturmação junto.

- [ ] **Step 4: Run the full suite**

Run: `rm -f data/merenda.mv.db && ./mvnw test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/br/com/frankcardoso/merenda/shared/config src/test/java/br/com/frankcardoso/merenda/shared/config
git commit -m "feat(demo): turmas e enturmacoes nos dados de demonstracao"
```

---

### Task 8: Feature `cadastro` no front

**Files:**
- Create: `src/app/features/cadastro/models/cadastro.models.ts`
- Create: `src/app/features/cadastro/data-access/aluno-api.service.ts`
- Create: `src/app/features/cadastro/data-access/turma-api.service.ts`
- Create: `src/app/features/cadastro/pages/aluno-page/aluno-page.component.{ts,html}`
- Create: `src/app/features/cadastro/pages/turma-page/turma-page.component.{ts,html}`
- Create: `src/app/features/cadastro/components/transferencia-form/transferencia-form.component.{ts,html}`
- Modify: `src/app/app.routes.ts`
- Test: `src/app/features/cadastro/components/transferencia-form/transferencia-form.component.spec.ts`

**Interfaces:**
- Consumes: endpoints da Task 6.
- Produces: rotas `/alunos` e `/turmas`, ambas `loadComponent`, no padrão já usado por `/cardapio`.

A tela do aluno lista o histórico de vínculos (turma, início, fim) e abre o formulário de transferência. Seguir o padrão de `features/cardapio`: `data-access` com o serviço HTTP, `models` com os contratos, `pages` com a rota, `components` com o formulário.

- [ ] **Step 1: Write the failing test**

```ts
describe('TransferenciaFormComponent', () => {
  it('exige turma e data da mudança antes de habilitar o envio')
  it('emite transferir com turmaId e dataMudanca')
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test --include='**/transferencia-form.component.spec.ts'`
Expected: FAIL

- [ ] **Step 3: Implement a feature**

- [ ] **Step 4: Run tests and build**

Run: `pnpm test --watch=false && pnpm build`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/app/features/cadastro src/app/app.routes.ts
git commit -m "feat(front): cadastro de aluno e turma com transferencia"
```

---

### Task 9: Dashboard lê `turmaId` opcional e o balde "Sem turma"

**Files:**
- Modify: `src/app/features/dashboard/models/indicadores.models.ts:24`
- Modify: `src/app/features/dashboard/components/indicadores-logisticos/indicadores-logisticos.component.{ts,html}`
- Test: `src/app/features/dashboard/components/indicadores-logisticos/indicadores-logisticos.component.spec.ts`

**Interfaces:**
- Consumes: `schemaVersion` 4 da Task 4.
- Produces: `PorTurma` ganha `turmaId?: string`.

- [ ] **Step 1: Write the failing test**

```ts
it('renderiza relatório antigo sem turmaId sem quebrar')   // indicadores_json schemaVersion 3
it('mostra a linha "Sem turma" com o mesmo destaque das demais')
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pnpm test --include='**/indicadores-logisticos.component.spec.ts'`
Expected: FAIL

- [ ] **Step 3: Implement** — `turmaId` opcional no modelo; a linha do balde usa o nome como chave quando não há id.

- [ ] **Step 4: Run tests and build**

Run: `pnpm test --watch=false && pnpm build`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/app/features/dashboard
git commit -m "feat(dashboard): turma com id e balde sem turma"
```

---

### Task 10: Documentação e validação ponta a ponta

**Files:**
- Modify: `README.md` (api), `../analytics-merenda-escolar/README.md`, `../front-merenda-escolar/README.md`
- Modify: `implementation_plan.md`

- [ ] **Step 1: Atualizar os três READMEs**

Registrar: turma como entidade, enturmação com vigência, turma carimbada no consumo, balde "Sem turma", `schemaVersion=4`, rotas novas. Tirar das pendências o que foi feito; manter aberta a adesão por turma, que continua dependendo de presença elegível.

- [ ] **Step 2: Subir os três serviços e conferir**

Run: `rm -f data/merenda.mv.db` e subir Java, Python e Angular. Abrir `/turmas`, `/alunos`, fazer uma transferência, bipar na fila e conferir o dashboard.
Expected: o atendimento aparece na turma vigente na data; o total de Atendimentos bate com o número de alunos que bipou.

- [ ] **Step 3: Commit**

```bash
git add README.md implementation_plan.md
git commit -m "docs: registrar turma, enturmacao e schema 4"
```
