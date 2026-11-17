# Análise IA para a secretaria Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Relatório de IA por refeição na janela de 30 dias, com plano de ação determinístico, tabela por item e resumo curto da IA, legível pela secretaria; e o "Não avaliável" falso corrigido.

**Architecture:** O Python calcula aceitação/desperdício sem depender de cardápio escolhido, ganha `sobraPercentual` por item, médias de atendimento na janela e `planoDeAcao` (schema 6). O Java aceita o schema 6, reescreve os textos determinísticos e restringe o LLM ao resumo. O front tira o passo do cardápio e redesenha o relatório.

**Tech Stack:** FastAPI + Pydantic + pytest; Spring Boot 3.5.16 / Java 25 + JUnit; Angular 20 + Karma.

**Spec:** `api-merenda-escolar/docs/superpowers/specs/2026-11-10-analise-ia-secretaria-design.md`

## Global Constraints

- Janela: 30 dias até a data de referência. Requisito: ≥ 20 dias com fechamento da refeição na janela; item precisa de ≥ 3 fechamentos para entrar em ação/tabela de aceitação.
- Limiares: aceitação 90% (meta PNAE, resto-ingestão); sobra na panela 10% do preparado; atendimento 80% do planejado.
- Fórmulas: aceitação = (servido − resto) / servido; sobra na panela = (preparado − servido) / preparado; nos textos, percentual em pt-BR com no máximo 1 casa e sem ",0" ("82%", "82,5%"); nos campos numéricos, `float` com 1 casa.
- Tipos de ação (strings exatas): `REVISAR_PREPARACAO`, `REDUZIR_PREPARO`, `REVER_PLANEJAMENTO`.
- Contrato Python→Java sobe para `schemaVersion "6"`; relatórios gravados com "5" continuam abrindo no front.
- `promptVersao = "v4-secretaria"`; LLM escreve só `resumoExecutivo` (2–3 frases, sem termos internos).
- Textos pt-BR com acento; nada de "API Java", "schemaVersion", provedor ou instante UTC na tela.
- Testes com dados fictícios; comentários só para o não óbvio; sem commits.
- Comandos: pytest `cd analytics-merenda-escolar && ./.venv/bin/python -m pytest -q` (base 55); Maven `JAVA_HOME=$HOME/.jdks/corretto-25.0.4.1 $HOME/.m2/wrapper/dists/apache-maven-3.9.16-bin/5grr65jo27hi51sujmtcldfovl/apache-maven-3.9.16/bin/mvn -B -q test` (base 237); front `PATH=$HOME/.nvm/versions/node/v20.19.0/bin:$PATH CHROME_BIN=/tmp/claude-1975829934/-home-frank-serrano/dd761c4d-f72d-487e-b365-b552c902be76/scratchpad/chrome-ns.sh pnpm test --watch=false --browsers=ChromeHeadless` (base 162) + `npx tsc -p tsconfig.app.json --noEmit`.
- API do Frank em :8081 (desta sessão, base `data/merenda`), analytics em :8000 (`uvicorn --reload`), `ng serve` em :4200 — não derrubar, não tocar em `data/`.

## Review Focus

1. Relatório pedido sem cardápio com fechamentos suficientes → aceitação e desperdício avaliados (o defeito) (Task 1).
2. Item com 2 fechamentos e aceitação baixa → fora do plano de ação (Task 1).
3. Relatório antigo schema 5 sem `planoDeAcao` → abre sem quebrar (Task 3).
4. Resposta do LLM que falha ou demora → o relatório conclui com o resumo determinístico (comportamento atual do fallback mantido) (Task 2).
5. Janela com dias sem cardápio da refeição → não entram nas médias de atendimento (Task 1).

---

### Task 1: Python — defeito, métricas por item, médias e plano de ação (schema 6)

**Files:**
- Modify: `analytics-merenda-escolar/app/domain/indicadores_logisticos.py` (`_medicao_para_periodo` ~l.417, `_base_insuficiente` ~l.441, `AceitacaoItem` ~l.164, `IndicadoresResponse` ~l.245, cálculo de execução)
- Modify: `analytics-merenda-escolar/README.md` (saída v6: `planoDeAcao`, `sobraPercentual`, médias)
- Test: `analytics-merenda-escolar/tests/domain/test_indicadores_logisticos.py`, `tests/api/test_indicadores_controller.py`

**Interfaces:**
- Produces (JSON camelCase):
  - `schemaVersion: "6"`.
  - `aceitacaoItens[]` e `aceitacaoItensSemana[]` ganham `sobraPercentual: float | null` ((preparado − servido)/preparado × 100, 1 casa; null se preparado = 0).
  - `execucaoPlanejamento` ganha `mediaAtendidosDia: float | null` e `mediaPlanejadaDia: float | null` (médias sobre os dias da janela com cardápio da refeição; null sem dias).
  - `planoDeAcao: [{ tipo, receitaId: str|null, item: str|null, texto: str, valor: float, referencia: float }]`.
- Entrada: hoje o Java manda `coberturaFechamentosCompleta`; sem escopo de cardápio a cobertura passa a vir dos fechamentos da janela (o Python não deve zerar itens medidos por causa do campo).

- [ ] **Step 1: Testes.**
  - `test_sem_escopo_de_cardapio_com_fechamentos_avalia_aceitacao_e_desperdicio` (Review Focus 1): request sem cardápio escolhido, 21 fechamentos com resto → `aceitacao.status` avaliável com percentual; `desperdicio` idem.
  - `test_sobra_percentual_por_item`: preparado 100, servido 82 → `sobraPercentual` 18.0.
  - `test_plano_revisar_preparacao_abaixo_de_90`: item com 6 fechamentos e aceitação 82% → ação `REVISAR_PREPARACAO` com texto "Revisar a preparação de Sopa de legumes: aceitação de 82% (meta 90%) em 6 dias medidos.", `valor` 82.0, `referencia` 90.0.
  - `test_plano_ignora_item_com_menos_de_3_fechamentos` (Review Focus 2).
  - `test_plano_reduzir_preparo_sobra_acima_de_10`: texto "Preparar menos Iogurte: sobrou na panela 18% do que foi preparado em 5 dias medidos."
  - `test_plano_rever_planejamento_abaixo_de_80`: médias 38 e 101 → "Rever a quantidade planejada: em média 38 alunos por dia para 101 planejados (38%)." e vem primeiro na lista.
  - `test_plano_vazio_quando_tudo_dentro_das_metas`.
  - `test_medias_ignoram_dias_sem_cardapio` (Review Focus 5).
  - `test_schema_6` no controller.
- [ ] **Step 2:** FAIL → implementar → pytest verde; README.

---

### Task 2: Java — schema 6, textos determinísticos e LLM só no resumo

**Files:**
- Modify: `api-merenda-escolar/src/main/java/br/com/frankcardoso/merenda/analytics/infrastructure/python/PythonIndicadoresLogisticosAdapter.java` (~:39 aceitar "6"), `analytics/application/port/IndicadoresLogisticos.java` (campos novos; fábrica de indisponível com "6"), records aninhados de item/execução/plano
- Modify: `.../gestao/application/IndicadoresDadosService.java` (~:118 cobertura sem escopo: verdadeira quando há fechamentos na janela)
- Modify: `.../inteligencia/application/port/ConclusaoDeterministica.java` (textos com acento, linguagem de gestão; `LIMITE_ACEITACAO` só sem medição), `IndicadoresParaLLM` (incluir plano de ação e médias), `.../inteligencia/infrastructure/**/ChatClientAnaliseLogisticaAdapter.java` (`INSTRUCAO` nova), `FakeAnaliseLogisticaAdapter` (resumo curto), `relatorio/domain/RelatorioIA.java` (`promptVersao` "v4-secretaria")
- Modify: `api-merenda-escolar/README.md` (contrato v6, prompt v4)
- Test: `PythonIndicadoresLogisticosAdapterTest`, `IndicadoresDadosServiceTest`, `ConclusaoDeterministicaTest`, `FakeAnaliseLogisticaAdapterTest`, `RelatorioIAWorkerTest`, `IndicadoresParaLLMTest`

**Interfaces:**
- Consumes: JSON da Task 1.
- Produces: `GET /api/v1/relatorios-ia/{id}` → `indicadores` com `planoDeAcao`, `aceitacaoItens[].sobraPercentual`, `execucaoPlanejamento.mediaAtendidosDia/mediaPlanejadaDia`, `schemaVersion "6"`; `promptVersao "v4-secretaria"`; `resultado.resumoExecutivo` em 2–3 frases. `GET /api/v1/gestao/indicadores` idem.
- Nova `INSTRUCAO` (texto a usar, pode ajustar a pontuação): "Você escreve para a secretaria de educação (nutricionista e setor de compras). Em 2 ou 3 frases, diga como está a refeição na janela analisada e qual é a ação mais importante, usando só os números do JSON. Não invente números, não use termos técnicos do sistema e não repita a lista de ações inteira. Preencha também evidencias e recomendacoes com listas vazias."

- [ ] **Step 1: Testes.** Adaptador aceita "6" e recusa "5" em resposta nova (relatórios antigos são lidos do banco, não passam pelo adaptador); `IndicadoresDadosService` sem `datasSelecionadas` com fechamentos → cobertura verdadeira; `ConclusaoDeterministica` sem acento faltando (ex.: "Aceitação alimentar não é avaliável" só quando não há medição); Fake gera resumo curto citando aceitação e a primeira ação; worker grava `promptVersao` v4; falha do LLM mantém o fallback atual (Review Focus 4).
- [ ] **Step 2:** FAIL → implementar → `$MVN test` verde; README.

---

### Task 3: Front — aba Análise IA para a secretaria

**Files:**
- Modify: `front-merenda-escolar/src/app/features/dashboard/pages/dashboard-page/dashboard-page.component.{ts,html,spec.ts}`
- Modify: `features/dashboard/models/dashboard.models.ts`, `features/dashboard/models/indicadores.models.ts`, `features/dashboard/data-access/dashboard-api.service.ts`
- Create (recomendado): `features/dashboard/components/relatorio-ia/relatorio-ia.component.{ts,html,spec.ts}` (exibição do relatório)
- Modify: `front-merenda-escolar/README.md`

**Interfaces:**
- Consumes: resposta da Task 2.
- Produces: `interface AcaoPlano { tipo: 'REVISAR_PREPARACAO' | 'REDUZIR_PREPARO' | 'REVER_PLANEJAMENTO'; receitaId: string | null; item: string | null; texto: string; valor: number; referencia: number }`; `AceitacaoItem.sobraPercentual?: number | null`; `execucaoPlanejamento.mediaAtendidosDia?/mediaPlanejadaDia?`; `planoDeAcao?: AcaoPlano[]`.

- [ ] **Step 1: Testes.**
  - gerar: sem passo de cardápio; texto "A análise usa os fechamentos de Lanche da manhã nos 30 dias até 09/11/2026. São necessários pelo menos 20 dias com fechamento; há 21."; botão "Gerar análise" habilitado com 21 e desabilitado com 19; `criarRelatorio` chamado com `{ dataReferencia, tipoRefeicaoId }` só (sem `receitaIds`/`datasSelecionadas`).
  - seletor: rótulo "09/11/2026 12:00 · Concluído" a partir de `criadoEm` ISO (hora local) e status traduzido (`PENDENTE` "Pendente", `PROCESSANDO` "Processando", `CONCLUIDO` "Concluído", `FALHOU` "Falhou"); sem provedor.
  - relatório v6: cabeçalho "Lanche da manhã · últimos 30 dias até 09/11/2026"; cartões Aceitação (percentual e "meta 90%"), Desperdício, Alunos atendidos ("38 por dia de 101 planejados"); "O que fazer" com os textos do plano na ordem; tabela ordenada pela aceitação crescente com destaque (< 90% e sobra ≥ 10%); "Leitura da IA" com o resumo; "Como foi calculado" recolhido contendo as limitações.
  - plano vazio → "Nenhuma ação necessária: todos os itens medidos estão dentro das metas."
  - relatório antigo (schema 5, sem `planoDeAcao`) → abre; sem "O que fazer"; evidências/recomendações antigas dentro de "Como foi calculado" sob "Texto da versão anterior" (Review Focus 3).
- [ ] **Step 2:** FAIL → implementar → testes verdes + `tsc`; README.

---

### Task 4: Validação ponta a ponta (controlador)

- [ ] Reiniciar a API desta sessão (perfis `gemini,local`, relógio 12:00; sem apagar a base); analytics recarrega sozinho.
- [ ] Gerar a análise do Lanche da manhã de hoje pela tela; conferir cartões avaliáveis, plano de ação, tabela, resumo e método; abrir um relatório antigo; console sem erro.

---

### Task 5: Demonstração com exemplos claros para a análise (pedido do Frank)

**Files:**
- Modify: `api-merenda-escolar/src/main/java/br/com/frankcardoso/merenda/shared/config/DadosDemonstracaoLoader.java` (medições sintéticas ~`garantirMedicoes(dia, refeicao, …)`; adesão ~:463 `TAXA_ADESAO`/`VARIACAO_ADESAO`)
- Modify (se a lista de perfis couber melhor lá): `.../shared/config/CatalogoItensDemonstracao.java`
- Test: `src/test/java/.../shared/config/DadosDemonstracao*Test.java`

**Perfis por receita (medições sintéticas, preparado 100):**
- **Pouco aceitos** — nomes contendo (sem diferenciar maiúsculas/acentos): "beterraba", "chuchu", "couve", "brocolis", "repolho", "cenoura": servidas 90–96, resto no prato = 20–30% do servido (aceitação ≈ 70–80%).
- **Produção acima do consumo** — "arroz", "macarrao", "pao": servidas 75–85 (sobra na panela 15–25%), resto 3–8% do servido.
- **Demais:** servidas 92–98 (sobra 2–8%), resto 3–8% do servido (aceitação ≥ 92%).
- Determinístico com o `Random` semeado já usado; mesma idempotência.

**Atendimento por refeição (consumo sintético dos dias passados):** a fração de alunos elegíveis com consumo AUTORIZADO no dia mira: Almoço 0,88 e Lanche da manhã 0,85 (sem ação de planejamento), Café da manhã 0,62 e Lanche da tarde 0,68 (com "Rever a quantidade planejada"), ± 0,05 de variação. Repetições continuam como hoje (não contam como aluno a mais na média? — `atendidos` conta AUTORIZADO, então repetição soma; manter a mira descontando a repetição média para que a média de atendidos/planejado fique dentro das faixas acima).

**Fechamentos:** todos os dias úteis passados com cardápio de demonstração das refeições semeadas ficam com medição completa (já é assim) — garante ≥ 20 dias por refeição com turma nos 30 dias.

- [ ] **Step 1: Testes.** Depois do boot com relógio fixo: para cada refeição com turma, ≥ 20 dias com fechamento completo nos 30 dias; itens "pouco aceitos" com aceitação média < 85%; itens "sobra alta" com sobra média ≥ 12%; demais com aceitação ≥ 90% e sobra < 10%; média atendidos/planejado: Almoço e Lanche da manhã ≥ 0,80, Café e Lanche da tarde < 0,75.
- [ ] **Step 2:** FAIL → implementar → `$MVN test` verde. README da API (seção Dados de demonstração): descrever os perfis e o que cada refeição mostra na análise.
