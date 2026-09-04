# Retrospectiva — o que erramos, o que acertamos e o que está aberto

Registro honesto do trabalho até aqui, para servir de base de decisão. Inclui os erros que eu
mesmo introduzi e o que os revelou — vários só apareceram porque você olhou a evidência com
atenção.

---

## Erros encontrados e corrigidos

### Suposições minhas que estavam erradas

**1. "Hard delete quebraria o histórico de relatórios."** Errado. `relatorio_ia` **não tem FK**
para `cardapio` — ele congela um snapshot dos dados em `dados_entrada_json` no momento da
geração. Relatórios antigos não quebram de jeito nenhum. A FK real está em
`auditoria_consumo.cardapio_id`, e é *essa* que justifica o soft delete. Argumentei a decisão
certa pelo motivo errado até verificar.

**2. Índice único parcial (`UNIQUE ... WHERE ativo = TRUE`).** Planejei isso no desenho e o **H2
não suporta** essa sintaxe (erro de sintaxe direto). Tive que remover a constraint do banco e
garantir a regra no `CardapioService`. Perde-se a garantia em nível de banco — em PostgreSQL o
índice parcial funciona e deveria ser recriado.

**3. Correlação atribuída sem dado que a sustentasse.** O vocabulário do cardápio ("arroz") não
casava com o do histórico ("Arroz branco") — **interseção zero**. A IA chegou a escrever *"itens
com menor execução listados, nenhum servido, reforçando baixa aceitação"*, ligando coisas sem
relação. Era o meu dado que induzia o erro, não o modelo.

**4. Payload mandava o ranking global errado.** Enviava os 10 piores itens **da base inteira**,
quando a pergunta útil é a taxa histórica **dos itens do cardápio de hoje**. Purê de moranga
(10,0%) e salada de alface (7,2%) ficavam de fora por serem "só" o 23º e o 33º pior de 36 — e o
cardápio do dia era justamente composto por eles.

### Bugs de dado

**5. `servida > planejada` em 3.434 registros.** No gerador, planejado e servido eram amostrados
de forma independente da mesma curva. Resultado visível na saída da IA: *"variação de 10.5% a
**115,3%**"* — taxa de execução acima de 100%, impossível. Corrigido: o servido vem da curva
medida (que foi medida sobre o servido) e o planejado deriva dele.

**6. `rng.sample` ignorando pesos.** Passei uma lista de tuplas `(nome, peso)` achando que
ponderava; `random.sample` sorteia uniforme. As refeições saíam com peso igual, e as de alta
execução (raras em produção) apareciam demais. Só apareceu porque a taxa global bateu 37,8%
contra alvo de 30,9%.

**7. Saturação no teto de alunos.** O fator de adesão usava como referência a média do catálogo
inteiro, mas o cardápio é composto majoritariamente por itens de alta execução — o fator ficava
~2,7x e **todos** os dias batiam no limite de 150 alunos. Referência corrigida para a média do
grupo de alta.

**8. Formato incompatível no seed.** O seed gravava `itens_json` como `["arroz","feijao"]` e o
CRUD novo espera `[{"nome":"arroz"}]`. Sem migração, a listagem quebraria na primeira leitura.
Resolvido com conversão na V3.

**9. `split(",")` no leitor.** Vários nomes reais contêm vírgula ("Bolo de maçã, sem açúcar",
"Lanche da tarde, menores de um ano"). Trocado para TSV.

### Bug que o rastro de produção revelou

**10. Provedor gravado errado.** Relatórios gerados pelo **Groq** ficavam registrados como
`gemini`. Motivo: `iniciar()` grava o provedor *configurado* e `concluir()` nunca atualizava com
quem de fato respondeu. Só foi possível provar porque você trouxe o CSV do console do Groq: duas
chamadas 200 no mesmo instante de relatórios que o banco dizia serem do Gemini. Corrigido —
`concluir()` agora sobrescreve com o provedor real, e há teste de domínio cobrindo isso.

### O elo que você achou

**11. O histórico nunca chegava ao serviço Python.** O `PythonPrevisaoConsumoAdapter` mandava
`List.of()` **hardcoded**. O Python foi construído para usar histórico:

| Função | Com histórico | Sem histórico (comportamento real até agora) |
|---|---|---|
| `_estimar_demanda` | média dos consumos anteriores | **devolve o consumo do próprio dia** (circular) |
| `_classificar_confianca` | ≥7 registros → ALTA | sempre **BAIXA** |
| `_montar_evidencias` | "usou N registros históricos" | **"Historico insuficiente"** |

Consequência: a previsão sempre foi circular, a confiança sempre baixa, e a frase "histórico
insuficiente" entrava no prompt **junto com** 20 dias de histórico enviados pelo Java — sinais
contraditórios no mesmo pedido, e frases prontas que o modelo reaproveitava em vez de fazer a
análise item a item. Sua hipótese estava certa.

### Comportamento não determinístico

**12. Temperatura 0.2 tornava o relatório irreprodutível.** Mesmo dado, mesmo prompt: uma
execução citou *"Salada de alface 5,3%, Purê de moranga 7,4%, Banana 42,4%"*, a seguinte falou
genérico *"itens com taxa abaixo da média"*. Para um relatório que deveria ser auditável isso é
ruim. Ajustado para temperatura 0 e o prompt passou a **exigir** a citação nominal com número.

---

## Acertos

**Mapeamento do legado.** Modelo de dados extraído de Liquibase + entidades JPA, com o achado
central: **o legado não registra consumo por aluno**. `REFEICOES_SERVIDAS_QTDS` é contagem
agregada por turno, sem FK para alunos. Isso definiu todo o desenho do protótipo.

**Validação com produção real.** Queries agregadas em dois tenants confirmaram e corrigiram
suposições: `TURMA` fragmentada (433 valores distintos para 22 escolas), taxa de execução de
~31% como **comportamento normal** e não defeito, e a curva do sistema sendo abandonado
(19 escolas → 1-2).

**Resiliência da chamada de IA.** Relatórios ficavam presos em `PROCESSANDO` para sempre e
travavam a fila inteira (pool de uma thread). Três camadas de limite + retry interno do SDK
desligado. O caso do 503 do Gemini passou a falhar em 10s em vez de ficar pendurado.

**Fallback entre provedores.** Gemini → Groq funcionando, comprovado no log do próprio console
do Groq (chamadas 200 no `openai/gpt-oss-20b`).

**CRUD de cardápio.** API + tela, soft delete preservando auditoria, 8 testes. Validado por API
ponta a ponta: duplicado → 409, data passada → 400, sem itens → 400, e reuso de data+turno
liberado após exclusão.

**Faker com fidelidade medida.** Não é dado inventado no chute — reproduz padrões medidos:

| Métrica | Gerado | Produção |
|---|---|---|
| Taxa de execução | 31,3% | 30,9% |
| Máximo | 494 | 494 |
| P90 | 144 | 140 |
| Distribuição por turno | 63/22/15 | 63/23/13 |
| Volume | 23,2k | 22,5k |

**A ideia do faker foi sua** e era melhor que a minha (exportar o agregado real). Zero dado de
produção no protótipo, controle dos padrões, e histórico do tamanho que quisermos.

**A correlação funcionando de verdade.** Cardápios com acompanhamentos de baixa execução rendem
**35** autorizados; com itens básicos, **104** — separação de 3x. E a IA passou a citar o
número: *"Salada de alface 5,3%, Purê de moranga 7,4%, Banana 42,4%"*.

---

## Dúvidas e decisões abertas

### 1. A arquitetura de refinaria (a mais importante)

Sua visão original: o Python recebe o volume cru, padroniza/condensa e só então manda pra IA.
Medição que sustenta a preocupação:

| Momento | Payload |
|---|---|
| Antes do histórico | 3.406 caracteres (~665 tokens, confirmado no log do Groq) |
| Com histórico | 3.800 caracteres (~950 tokens) |

Isso com **1 escola**. Em 20 escolas o payload projeta ~19 mil tokens por relatório, e com
3 turnos × 250 dias são ~15 mil relatórios/ano. Além do custo, prompt grande piora a precisão.

**Aberto:** quem chama o LLM?
- Python refina, Java continua chamando — aproveita timeout/retry/fallback já construído e testado
- Python refina e chama direto — mais próximo do que você descreveu, mas exige replicar essa infra lá

### 2. Ambiguidade que nenhum dado resolve

Taxa de execução baixa é **baixa aceitação** (criança não comeu) ou **falha de registro**
(cozinheira não lançou)? Os dados não distinguem. Indícios de que pode ser registro: "Couve com
farofa" foi executada 1 vez com média **0,0**, e ≥10% de todas as execuções registram quantidade
zero. Isso limita o que se pode afirmar — hoje o prompt obriga a ressalvar. Vale decidir se o
produto assume essa ambiguidade ou se busca resolvê-la na origem (ex.: campo de motivo no
registro).

### 3. Metade da medição de itens está faltando

A Q-F foi ordenada ascendente com limite de 40 — são os **40 piores**. O grupo de alta execução
está **calibrado, não medido** (43,7% deduzido por bisseção). Rodar a Q-F com
`ORDER BY pct_execucao DESC` trocaria a estimativa por dado real.

### 4. Fidelidade × demonstrabilidade

Duas escolhas conscientes que sacrificam fidelidade para o padrão ficar visível:
- Variação diária reduzida de ±12pp (medido) para ±4pp — com o ruído real, o efeito do cardápio
  ficava indistinguível de acaso
- Dias "ruins" levam 2 itens de baixa execução, não 1

Estão comentadas no código. **Aberto:** é o compromisso certo, ou a demo deveria mostrar o ruído
real e aceitar que a correlação não salta aos olhos?

### 5. O histórico envelhece em silêncio

As datas ficam congeladas no TSV no momento da geração. Funciona hoje, mas a série de 20 dias vai
ficando velha e, depois de ~120 dias, o ranking de itens **esvazia** (janela da consulta).
Opções: regerar periodicamente (manual) ou o loader ancorar as datas em relação a hoje (elimina a
manutenção).

### 6. `INTEGRAL` é modalidade, não horário

Foi adicionado ao enum `Turno` porque é o turno mais usado em produção (e `NOITE` nunca foi
usado). Mas o `TurnoResolver` da fila resolve pelo relógio da catraca e **nunca retorna
INTEGRAL**. Consequência: um cardápio INTEGRAL não acumula consumo pelo fluxo real da fila.
Funciona para planejamento e histórico; para a fila, não.

### 7. Cardápio por dia não escala

O protótipo modela um cardápio por (data, turno); o legado usa **template semanal** (matriz dia
da semana × refeição × receitas). Para uso real seria preciso cadastrar dia a dia. Aceitável no
escopo atual, mas é o primeiro ponto a revisitar.

### 8. Pendências operacionais

- **Suíte de testes não rodou** depois das últimas mudanças (`HistoricoConsumoService.resumir`
  mudou de assinatura, `PrevisaoConsumoInput` ganhou campo)
- **Nada commitado** desde `Turno.INTEGRAL` — faker, tabela de histórico, loaders, refinamentos
- A chave do Gemini ficou exposta no `application-local.example.yml` em algum momento (o arquivo
  está limpo agora, mas a chave circulou na árvore de trabalho — **vale rotacionar**)
