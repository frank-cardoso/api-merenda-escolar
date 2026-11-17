# Análise IA para a secretaria — desenho

Data: 2026-11-10. Repositórios: `analytics-merenda-escolar`, `api-merenda-escolar`, `front-merenda-escolar`.
Mapa do fluxo atual (investigação): `scratchpad/ia-mapa.md` da sessão; os pontos citados aqui vêm dele.

## Intenção

O Frank achou a aba "Análise IA" do Dashboard difícil de entender. Conferindo na tela (relatório do Lanche da manhã de
09/11):
- o passo "1. Escolha o cardápio" exige um cardápio com 20 dias completos; com a demonstração todo cardápio aparece
  "1/20 dias completos" (só o Almoço passa, no limite), mas há relatório salvo ao lado;
- o relatório disse "Aceitação: Não avaliável" e "não há medição de resto no prato" com 21 fechamentos e 11 itens
  medidos — **defeito** (ver "Defeito");
- é um bloco de números sem hierarquia (5 frases no resumo, 13 em "Evidências", métricas misturadas, "API Java"
  vazando), com um parágrafo final de método sem acento e o seletor de relatórios mostrando instante UTC e "groq".

### Quem lê (pesquisa)

Pelo PNAE a compra é da **Entidade Executora** (secretaria/prefeitura) na gestão centralizada, ou da escola (Unidade
Executora / caixa escolar) na descentralizada (Resolução CD/FNDE 4/2026). O cardápio é do **nutricionista
responsável**, que também aplica o teste de aceitabilidade (resto-ingestão com meta de 90%). O CAE fiscaliza
aceitabilidade e uso do recurso. **Decisão do Frank: leitor principal é a secretaria (nutricionista + compras).**

Para esse leitor o relatório responde, nesta ordem:
1. **Como está esta refeição?** aceitação contra a meta de 90%, desperdício, alunos atendidos contra o planejado;
2. **O que fazer?** ações concretas por item;
3. **Por quê?** números por item; método em "Como foi calculado", recolhido.

## Metas

- A análise é da **refeição na janela** (últimos 30 dias até a data de referência), por item servido nesse período,
  não de um cardápio exato. O pedido não precisa escolher cardápio.
- Requisito único e visível: pelo menos 20 dias com fechamento desta refeição nos 30 dias (já é a regra do backend).
- Relatório com cabeçalho, três cartões, plano de ação, tabela por item, leitura da IA e método recolhido.
- Corrigir o "Não avaliável" falso.

## Fora de escopo

- Teste de aceitabilidade por escala hedônica; custo em reais; comparação entre escolas.
- Escolher janela de 7 dias para o relatório (a aba Indicadores continua com 7/30 para aceitação dos itens).
- Apagar relatórios antigos: continuam abrindo (ver "Compatibilidade").

## Anti-metas

- Ação recomendada sem número que a sustente na própria linha.
- Texto da IA contradizendo os números determinísticos (a IA só redige o resumo; classificações, ações e tabela são
  determinísticas).
- Termos internos na tela: "API Java", "schemaVersion", "provedor", instante UTC.

## Defeito: "Não avaliável" falso

`analytics-merenda-escolar/app/domain/indicadores_logisticos.py` `_medicao_para_periodo` (~l.417) conta
`itens_medidos = 1 if cobertura_fechamentos_completa else 0`; o Java manda `coberturaFechamentosCompleta =
!datasDoEscopo.isEmpty()` (`IndicadoresDadosService:118`), e sem cardápio escolhido `datasDoEscopo` é vazio. Resultado:
todo relatório sem cardápio sai `NAO_AVALIAVEL`. Correção: sem escopo de cardápio, a cobertura do período é dada pelos
fechamentos da refeição na janela (há fechamento → avaliável; os mínimos de porções e de fechamentos continuam). A
limitação fixa "não há medição de resto no prato" (`ConclusaoDeterministica:28`) só aparece quando de fato não há
medição.

## Plano de ação (determinístico, no Python)

Novo bloco `planoDeAcao` na resposta de indicadores (contrato sobe para `schemaVersion "6"`), lista ordenada:

| Tipo | Quando | Texto (exemplo) |
|---|---|---|
| `REVISAR_PREPARACAO` | item com ≥ 3 fechamentos na janela e aceitação < 90% | "Revisar a preparação de Sopa de legumes: aceitação de 82% (meta 90%) em 6 dias medidos." |
| `REDUZIR_PREPARO` | item com ≥ 3 fechamentos e sobra na panela ≥ 10% do preparado | "Preparar menos Iogurte: sobrou na panela 18% do que foi preparado em 5 dias medidos." |
| `REVER_PLANEJAMENTO` | média de alunos atendidos por dia na janela < 80% do planejado médio | "Rever a quantidade planejada: em média 38 alunos por dia para 101 planejados (38%)." |

Cada ação: `{tipo, receitaId|null, item|null, texto, valor, referencia}` (`valor` e `referencia` em %). Ordem:
`REVER_PLANEJAMENTO` primeiro, depois itens por gravidade (maior distância da referência). Sem ação → lista vazia e o
front mostra "Nenhuma ação necessária: todos os itens medidos estão dentro das metas."

Aceitação por item = (servido − resto) / servido; sobra na panela = (preparado − servido) / preparado — os mesmos
dados de `aceitacaoItens`, que ganha `sobraPercentual`.

Média de atendidos e planejado médio na janela: novos campos `mediaAtendidosDia` e `mediaPlanejadaDia` em
`execucaoPlanejamento` (ou bloco próprio), calculados sobre os dias da janela com cardápio da refeição.

## Java

- Aceitar `schemaVersion "6"` no adaptador (`PythonIndicadoresLogisticosAdapter:39`) e no contrato
  (`IndicadoresLogisticos`); repassar `planoDeAcao`, `sobraPercentual` e as médias.
- Mandar ao Python a cobertura certa sem escopo (ou deixar o Python decidir — ver "Defeito").
- `ConclusaoDeterministica`: textos com acento, linguagem de gestão; limitação de aceitação só quando falta medição.
- Prompt `v4-secretaria`: o LLM escreve só `resumoExecutivo` em 2–3 frases para a secretaria (nutricionista e
  compras), sem termos internos, citando os números que recebeu; `evidencias`/`recomendacoes` deixam de ser exibidos
  (o contrato do LLM pode mantê-los; o Fake adapter passa a gerar um resumo curto). `promptVersao = "v4-secretaria"`.

## Front (aba "Análise IA")

- **Gerar:** some o passo "Escolha o cardápio". Fica: "A análise usa os fechamentos de {refeição} nos 30 dias até
  {data}. São necessários pelo menos 20 dias com fechamento; há {n}." e o botão "Gerar análise" (habilitado com
  n ≥ 20). `n` vem de `quantidadeFechamentosDoMes` dos indicadores já carregados. O pedido não envia
  `receitaIds`/`datasSelecionadas`.
- **Seletor de relatórios salvos:** "dd/mm/aaaa HH:mm · Concluído" (hora local; status traduzido; sem provedor).
- **Relatório:**
  1. Cabeçalho: "{refeição} · últimos 30 dias até dd/mm/aaaa" e "Gerado em dd/mm/aaaa HH:mm".
  2. Três cartões: **Aceitação** (percentual, meta 90%, nível), **Desperdício** (percentual, nível), **Alunos
     atendidos** (média por dia contra o planejado, %). Não avaliável mostra o motivo curto.
  3. **O que fazer:** a lista do `planoDeAcao`, com ícone por tipo; vazia → mensagem acima.
  4. **Itens da refeição:** tabela Item | Aceitação | Sobra na panela | Resto no prato | Dias medidos, ordenada pela
     aceitação crescente, aceitação < 90% e sobra ≥ 10% destacadas.
  5. **Leitura da IA:** `resumoExecutivo`.
  6. **Como foi calculado** (recolhido): fórmulas, janela, mínimos e as limitações (`observacaoLimitacoes`).

## Compatibilidade

Relatórios antigos (`schemaVersion "5"`, sem `planoDeAcao`) abrem: cartões e tabela com o que existir, "O que fazer"
omitido, e "Evidências"/"Recomendações" antigas mostradas dentro de "Como foi calculado" como "Texto da versão
anterior". O pedido antigo com `receitaIds`/`datasSelecionadas` continua aceito pela API.

## Decisões do Claude

- Janela fixa de 30 dias; limiares 90% (aceitação, PNAE) e 10% (sobra na panela) e 80% (atendimento, meta interna
  já usada).
- Plano de ação no Python (onde estão as métricas), não no LLM.
- LLM restrito ao resumo; evidências/recomendações livres saem da tela.

## Validação esperada

- Python: defeito (sem escopo, com fechamentos → avaliável), plano de ação (cada tipo, ordem, lista vazia),
  `sobraPercentual`, médias da janela, schema 6.
- Java: adaptador aceita 6, textos de `ConclusaoDeterministica`, prompt v4, Fake.
- Front: gerar sem cardápio, requisito com contagem, layout do relatório, relatório antigo, rótulo do seletor.
- Tela: gerar a análise do Lanche da manhã e conferir cartões, ações e tabela.

## Fontes

- Resolução CD/FNDE 4/2026: https://www.gov.br/fnde/pt-br/acesso-a-informacao/legislacao/resolucoes/2026/resolucao-cd_fnde-no-4-de-26-de-fevereiro-de-2026.pdf
- FNDE, gestão descentralizada: https://www.gov.br/fnde/pt-br/acesso-a-informacao/acoes-e-programas/programas/pnae/media-pnae/encontros-tecnicos/Fala3Mesa3ExperinciasobreaexecuodoPNAEnagestodescentralizada.pdf
- Aceitabilidade (resto-ingestão 90%): https://unifafibe.com.br/revistasonline/arquivos/cienciasnutricionaisonline/sumario/91/23042020165409.pdf ; FNDE, grupo técnico de testes: https://www.gov.br/fnde/pt-br/acesso-a-informacao/acoes-e-programas/programas/pnae/manuais-e-cartilhas/relatorio_grupo_tecnico_testes.pdf
- CAE: https://www.mprj.mp.br/documents/20184/1216089/MANUAL_CAE.pdf ; https://educacao.prefeitura.rio/wp-content/uploads/sites/42/2023/11/PLANO-DE-ACAO-CAE-2024-1.pdf
