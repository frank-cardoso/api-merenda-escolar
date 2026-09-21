# Decisoes tecnicas — resiliencia de IA e cadastro de cardapio

Registro das decisoes tomadas nesta branch, com o motivo por tras de cada uma. O objetivo e
que quem ler depois entenda **por que** o codigo esta assim, nao apenas o que ele faz.

## 1. Resiliencia na chamada de IA

### Problema observado

Relatorios ficavam presos em `PROCESSANDO` indefinidamente. O pool de execucao tem apenas uma
thread (`AsyncConfig.relatorioExecutor`, `corePoolSize=1`), entao um relatorio travado bloqueava
todos os seguintes, que ficavam empilhados em `PENDENTE` sem nunca iniciar.

A causa nao era uma so — eram tres camadas de espera empilhadas, cada uma escondendo a anterior:

1. **Sem timeout HTTP no cliente Gemini.** O `com.google.genai.Client` autoconfigurado pelo
   Spring AI nao define timeout de conexao/leitura. Uma chamada que travava sem retornar erro
   ficava presa no socket para sempre.
2. **Retry interno do SDK do Google.** O `google-genai` faz retry por conta propria
   (`RetryInterceptor`), independente do `RetryTemplate` do Spring AI. Os dois se empilhavam e o
   tempo total estourava qualquer limite razoavel.
3. **Retry do Spring AI com backoff agressivo.** O default e `maxAttempts=10` com
   `multiplier=5` (2s, 10s, 50s...). Duas tentativas ja passavam de um minuto.

### Solucao

Tres limites em camadas, do mais especifico para o mais generico:

| Camada | Onde | Valor | Papel |
|---|---|---|---|
| Timeout HTTP | `GoogleGenAiClientConfig` / `GroqClientConfig` | 10s | Corta a chamada no socket |
| Retry | `spring.ai.retry` (application-gemini.yml) | 2 tentativas, backoff 1s→2s | Absorve falha transitoria |
| Timeout de aplicacao | `merenda.ia.timeout-segundos` | 50s | Rede de seguranca do worker |

O retry interno do SDK do Google foi desligado (`HttpRetryOptions.attempts(1)`) para nao
duplicar o que o Spring AI ja faz.

**Atencao ao mexer nesses numeros:** o timeout de aplicacao precisa ser maior que o pior caso
das camadas de baixo somadas (Gemini + Groq em sequencia). Se ficar menor, o worker desiste
antes dos provedores terminarem e a mensagem de erro perde a causa real.

### Limitacao conhecida

`CompletableFuture.get(timeout)` **nao cancela** a tarefa subjacente — apenas para de esperar
por ela. A chamada HTTP continua rodando em background ate terminar sozinha. Por isso o timeout
HTTP (camada 1) e o que realmente importa; o de aplicacao e so uma rede de seguranca.

## 2. Fallback entre provedores de IA

`FallbackAnaliseLogisticaAdapter` tenta o Gemini e, se falhar por qualquer motivo, aciona o Groq
antes de desistir. Motivo: o free tier do Gemini tem cota de 20 requisicoes/dia e retorna 503
("high demand") com frequencia — um unico provedor sobrecarregado derrubava todos os relatorios.

Os dois provedores coexistem porque o `OpenAiChatModel` do Groq e montado manualmente em
`GroqClientConfig`, sem passar pela autoconfiguracao do starter openai (que fica desligada por
`spring.ai.model.chat=google-genai`). As autoconfiguracoes nao usadas do starter openai
(embedding, image, audio, moderation) sao excluidas em `application.yml`, senao exigem
`spring.ai.openai.api-key` no boot e derrubam a aplicacao.

### Rastreabilidade do provedor (bug corrigido)

`RelatorioIA.iniciar()` grava o provedor **pretendido** (configurado), mas quem responde pode
ser outro quando o fallback entra. O relatorio ficava marcado como `gemini` mesmo tendo sido
gerado pelo Groq — evidencia disso apareceu no console do Groq: chamadas 200 registradas no
mesmo instante de relatorios que o banco dizia serem do Gemini.

Correcao: `AnaliseLogisticaPort` ganhou `provedor()` e `modelo()`, e `RelatorioIA.concluir()`
agora sobrescreve esses campos com quem **de fato** atendeu. O `FallbackAnaliseLogisticaAdapter`
usa `ThreadLocal` para reportar o atendente da ultima chamada — seguro porque `analisar()` e a
leitura de `provedor()`/`modelo()` acontecem sempre na mesma thread, dentro do mesmo lambda do
`RelatorioIAWorker`.

## 3. Cadastro de cardapio

### Comparacao com o legado

No sistema legado (Betha Merenda), `CARDAPIOS` e um **periodo de validade**
(`DT_INICIO`/`DT_FIM`) associado por tabelas de juncao a escola, turno, dia da semana e grupo de
consumo. A composicao vem de `REFEICOES` → `RECEITAS` → `INGREDIENTES` (tres tabelas
encadeadas), e a tela de cadastro e uma matriz semanal (dia da semana x refeicao x receitas).

Aqui o modelo e deliberadamente mais enxuto: **um cardapio = uma data + um turno**, com os itens
em `itens_json`. Isso colapsa cinco tabelas do legado em uma. A contrapartida e que nao existe
cardapio "modelo semanal" — para uma semana inteira e preciso cadastrar dia a dia. Aceitavel no
escopo de prototipo, mas e o primeiro ponto a revisitar se o sistema crescer.

### Soft delete

Excluir um cardapio marca `ativo = false` em vez de remover a linha. Motivo concreto:
`auditoria_consumo.cardapio_id` referencia o cardapio servido, e esse historico de quem comeu o
que nao pode ser perdido. Com hard delete seria preciso escolher entre bloquear a exclusao
(erro de FK) ou apagar a auditoria junto (`CASCADE`) — as duas opcoes piores.

Vale notar que `relatorio_ia` **nao** tem FK para cardapio: ele guarda um snapshot dos dados em
`dados_entrada_json` no momento da geracao. Relatorios antigos nao quebram de nenhum jeito.

### Constraint unica removida (V3)

O ideal seria um indice unico parcial (`UNIQUE(data, turno) WHERE ativo = TRUE`), permitindo
varias linhas historicas inativas e apenas uma ativa. **O H2 nao suporta essa sintaxe.**

Solucao adotada: remover a constraint do banco e garantir a regra no `CardapioService` via
`existsByDataAndTurnoAndAtivoTrue`. Perde-se a garantia em nivel de banco — aceitavel em um
prototipo mono-usuario, mas seria preciso rever ao migrar para PostgreSQL (onde o indice parcial
funciona e deveria ser recriado).

A mesma migracao converte o formato antigo de `itens_json` (`["arroz","feijao"]`) para o novo
(`[{"nome":"arroz","quantidade":null}]`), porque o cadastro passou a ter quantidade por item.

## 4. Quem decide o numero e quem redige

### Problema observado

Um relatorio saiu recomendando *"aumentar o planejamento de refeicoes para atingir a meta interna
de 80%"*. A execucao e `autorizacoes / planejado`: com 91 autorizacoes e 300 planejadas, aumentar
o denominador faz o percentual **cair**. O modelo errou a direcao de uma proporcao inversa.

Junto vieram outros tres, do mesmo tipo:

| Saida do modelo | Por que estava errada |
|---|---|
| Desperdicio "ALTO" | Nao havia medicao de sobra nenhuma |
| Previsao de 78 | A catraca ja tinha registrado 91 naquele dia |
| "Queda de execucao do cuscuz" | Posicao em ranking estatico, sem serie temporal |

O "ALTO" **nao foi alucinacao**. Vinha cravado do Python: `_classificar_risco` devolvia ALTO
quando `demanda / planejada < 0,6`. O campo chegava mastigado no payload e o modelo so copiou.
O mesmo vale para a recomendacao de ajuste: o Python enviava `ajuste_sugerido = demanda -
planejada`, um numero que **e** uma sugestao de direcao operacional. O modelo nao inventou a ideia
de mexer no planejamento, ele leu do payload; so errou o sinal.

### Solucao

O codigo deterministico fecha a conclusao numerica antes do modelo ver o payload, e o modelo
redige a partir dela.

- `ConclusaoDeterministica` monta as frases com os numeros do dia e as limitacoes aplicaveis.
- `RelatorioIAWorker.comClassificacoesDeterministicas` sobrescreve `nivelAceitacao` e
  `riscoDesperdicio` da saida do modelo com os valores calculados. O modelo nao vota nesses dois.
- O prompt encolheu de 2.621 para 2.015 caracteres: saiu a descricao de dominio que convidava o
  modelo a concluir, entraram proibicoes diretas do que ja se viu ele fazer.
- No Python, `_classificar_risco` foi removido e `ajuste_sugerido` virou
  `diferenca_previsao_planejamento` — descritivo, sem verbo de acao.

### Por que nao few-shot

A alternativa considerada era dar um exemplo de resposta pronta no prompt. Foi descartada: o item
12 da RETROSPECTIVA ja registra este modelo reaproveitando frase pronta em vez de analisar. Texto
de exemplo fixo vira molde aplicado em dia que nao se encaixa.

A conclusao deterministica cumpre o papel do exemplo **com os numeros do proprio dia**, entao nao
ha o que copiar de um dia para outro.

### Previsao com piso no realizado

`_estimar_demanda` devolvia a media dos dias anteriores, ignorando o que ja tinha acontecido hoje.
Agora e `max(media_historica, consumos_autorizados)`, com `media_historica`, `piso_realizado` e
`origem_estimativa` expostos separados — sem os tres campos nao da para explicar qual prevaleceu.

## 5. Projecao para o prompt separada da fotografia persistida

### Problema observado

O mesmo objeto `IndicadoresLogisticos` ia para o LLM e era gravado em `relatorio_ia.
indicadores_json`, que a tela de Indicadores le. Os dois consumidores querem coisas diferentes.

Medicao do payload real (8.775 caracteres):

| Bloco | Caracteres | % |
|---|---|---|
| `indicadores` | 4.931 | 56,2% |
| `conclusaoDeterministica` | 1.149 | 13,1% |
| `itensComMenorExecucao` | 932 | 10,6% |
| `previsaoConsumo` | 834 | 9,5% |

Dentro de `indicadores`, `porTurma` sozinho custava 1.918 caracteres: nove turmas, **todas**
`percentual: null` e `statusMeta: NAO_AVALIAVEL`, com o mesmo `motivo` de 68 caracteres repetido
nove vezes. E o prompt proibe explicitamente concluir sobre esse bloco.

### Solucao

`IndicadoresParaLLM` projeta a fotografia para o prompt. Colapsa `porTurma` em `resumoTurmas`
**apenas quando toda turma esta NAO_AVALIAVEL** — assim que alguma ganhar percentual proprio, o
detalhe volta sozinho. O worker grava o objeto completo e envia a projecao.

Resultado medido no mesmo turno: payload 8.775 -> 6.838 caracteres (-22,1%). Nos logs do provedor,
entrada de 3.318 -> 2.800 tokens (-15,6%), abaixo dos 3.011-3.033 de antes de tudo isso.

## 6. Medicao de sobra, e o que ela nao mede

### Problema observado

`nivelAceitacao` e `riscoDesperdicio` passaram a sair NAO_AVALIAVEL em todo relatorio. Estava
correto — o sistema so sabia quantas autorizacoes a catraca registrou, e isso nao mede nem
ingestao nem perda — mas deixava dois cards do painel permanentemente mortos.

### Duas perdas, nao uma

`medicao_sobra` (V6, ajustada na V7) guarda as duas separadas, em porcoes, por receita:

- `sobra_nao_distribuida`: ficou na cuba, nunca chegou ao prato. Erro de producao.
- `resto_no_prato`: foi servido e voltou. **A unica das duas que mede rejeicao.**

Somar num unico "desperdicio" misturaria falha de dimensionamento com recusa do aluno, que e
justamente a confusao que a secao 4 existe para desfazer. Dai `aceitacao = (servido - resto) /
servido` e `desperdicio = (sobra + resto) / preparado`.

Unidade em porcoes porque o resto do sistema conta refeicoes; o cardapio registra "15 kg", e
converter exigiria fator por receita que o prototipo nao tem.

### Tres minimos, todos nomeados

| Portao | Valor | Onde |
|---|---|---|
| Cobertura do cardapio | 100% dos itens | `_base_insuficiente` |
| `PORCOES_MINIMAS_DIA` | 30 | aceitacao e desperdicio do dia |
| `AMOSTRA_MINIMA_INGREDIENTE` | 30 | ranking por ingrediente |

Cobertura parcial e recusada porque aceitacao de um prato nao e aceitacao do cardapio, e o card do
painel mostra so o rotulo — quem olha nao ve que faltaram tres itens. O piso de porcoes barra o
caso degenerado: 3 servidas com 1 de resto dao 66,7%, um numero que existe e nao significa nada.

`itensMedidos` conta so receitas do cardapio (`m.receitaId in :receitasDoCardapio`). Sem esse
filtro, uma medicao de receita fora do cardapio inflaria o numerador e a cobertura fecharia sem ter
fechado.

**Consequencia operacional:** com cobertura total, esquecer um item derruba o indicador do dia
inteiro. Se isso zerar muitos dias na pratica, o ajuste certo nao e afrouxar o portao — e a tela
de lancamento nao deixar fechar o turno com item faltando.

### Ingrediente: diferenca contra a base, nao media isolada

A media isolada engana. Na janela medida, batata aparece com 29,84% de resto — mas so porque
divide a sopa de legumes com o chuchu (29,89%). O resto e medido por prato inteiro, entao
ingredientes servidos juntos dividem o mesmo numero.

Por isso o Python compara cada ingrediente contra a media da janela e reporta
`diferenca_base_pp` junto com a amostra. A contaminacao continua existindo — e por isso a
limitacao "associacao observada, nao causa comprovada" entra na conclusao sempre que ha
ingrediente destacado.

### Origem do dado

O faker (`scripts/gerar_historico_fake.py`) deriva a medicao das **mesmas linhas** de historico ja
geradas: `porcoes_servidas` E a `quantidade_servida` do historico, e o resto deriva dela. Amostrar
duas curvas independentes foi o que produziu servido acima do planejado numa versao anterior
(item 5 da RETROSPECTIVA).

A rejeicao por ingrediente e hipotese do prototipo, nao medicao — e esta calibrada para acompanhar
os grupos ja medidos: ingredientes que dominam os itens `BAIXA_EXECUCAO` rejeitam mais. Sem essa
coerencia, item com execucao baixa apareceria com resto baixo e os dois sinais se contradiriam
dentro do mesmo payload.

As faixas (`ACEITACAO_ALTA = 90`, `ACEITACAO_MEDIA = 75`, `DESPERDICIO_BAIXO = 10`,
`DESPERDICIO_MEDIO = 20`) tambem sao convencao do prototipo. Ficam nomeadas no topo do modulo para
quem tiver referencia real de nutricao escolar saber onde trocar.

**Atencao ao regerar o faker:** `historico-fake.tsv` e `medicao-sobra-fake.tsv` so fazem sentido
juntos. Os loaders so rodam com a tabela vazia, entao recarregar um sem o outro deixa `topComidas`
e a medicao vindo de sorteios diferentes. Apague as duas tabelas.

## 7. Identidade de item por id

### Problema observado

`item` era `VARCHAR` livre em tres tabelas, casado por nome. Isso ja tinha produzido o defeito do
item 3 da RETROSPECTIVA: o vocabulario do cardapio ("arroz") nao casava com o do historico ("Arroz
branco"), intersecao zero, e o modelo escreveu correlacao sobre nada.

O conserto de entao — comparar nome normalizado, com `contains` nos dois sentidos — criou um
problema novo. Passamos a ter **duas regras de identidade incompativeis no mesmo sistema**:
substring no historico e igualdade exata na medicao. Com substring, "Arroz" casa tanto com "Arroz
branco" quanto com "Arroz com galinha".

E e o mesmo vicio que o nosso proprio mapeamento critica no legado, onde `TURMA` e `VARCHAR2(80)`
livre com 433 valores distintos para 22 escolas.

### Solucao

`receita` e `ingrediente` (V7) sao catalogo com identidade propria. `historico_consumo`,
`medicao_sobra` e `receita_ingrediente` apontam para `receita(id)` por FK; a coluna de texto deixou
de existir nas tres. `Receita.nome` e rotulo: pode ser corrigido sem quebrar junção nenhuma.

`HistoricoConsumoService.normalizar()` e `correspondeAoCardapio()` foram **removidos**. Nao ha mais
casamento aproximado em lugar nenhum do sistema.

`codigo_externo` existe nas duas tabelas de catalogo e esta vazio: e onde `RECEITAS.ID` e
`INGREDIENTES.ID` do legado entram numa integracao, sem precisar tocar nas FK internas.

### Como o id e gerado

`uuid5` sobre o nome, com namespace fixo no faker (`NAMESPACE_MERENDA`). Deterministico: regerar os
TSV nao troca os ids, e os quatro arquivos (`itens-catalogo`, `historico-fake`,
`medicao-sobra-fake`, `receitas-ingredientes`) levam id e nome juntos — id para o vinculo, nome para
quem le o arquivo.

### O cadastro de cardapio

`ItemCardapioDto` ganhou `receitaId`. No cadastro ele e opcional: quando vem nulo, o servico resolve
pelo **nome exato** do catalogo e recusa nome desconhecido com HTTP 400. Isso manteve o formulario
atual funcionando sem reintroduzir casamento aproximado — o que fica gravado sempre tem id.

`GET /api/v1/receitas` expoe o catalogo para o front virar seletor em vez de campo de texto.

**Cardapios gravados antes da V7 ficam com `receitaId` nulo** e por isso nao participam de medicao
nem de analise por ingrediente, ate serem regravados. A V7 nao faz backfill de proposito: seria uma
ultima passada de casamento por nome, exatamente o que ela existe para eliminar.

### Ordem dos loaders

`@Order(1)` no catalogo, `(2)` no historico, `(3)` na medicao. A ordem entre listeners do mesmo
`ApplicationReadyEvent` nao e garantida, e sem ela a FK para `receita` estoura.

### O grafo vai no payload

`IndicadoresLogisticosInput.receitasMedidas` leva, por receita, a composicao dela mais a medicao. O
servico Python nao conhece tabela de ingrediente nenhuma e calcula a propria linha de base — e isso
que o deixa portavel para outra base sem mudanca.

Antes, a atribuicao a ingrediente era um `group by ingrediente` em SQL. Isso embutia a coocorrencia
antes de qualquer analise, sem deixar rastro: batata herdava a rejeicao do chuchu por dividirem a
sopa de legumes.

### Presenca contra ausencia, e o que ela nao resolve

O Python compara o resto dos pratos que levam o ingrediente com o dos pratos que nao levam. Em dado
com variacao isso separa: no teste unitario, chuchu +24,5 pp contra batata +3,0 pp — a batata cai
uma ordem de grandeza porque tambem aparece no pure, de resto baixo.

No turno TARDE do faker atual, **nao separa**, e esta correto que nao separe:

```
chuchu   medido em TARDE/180d: Sopa de legumes (39), Chuchu (1)
batata   medido em TARDE/180d: Sopa de legumes (39)
cenoura  medido em TARDE/180d: Sopa de legumes (39), Cenoura ralada/refogada/cozida (1 cada)
```

Batata so tem medicao na sopa. Os tres sao colineares, nenhum metodo separa variaveis colineares, e
os tres saem com a mesma diferenca. **O faker nao foi ajustado de proposito**: numa escola que so
serve salsicha com macarrao tambem e impossivel isolar o culpado, e o sistema apontar a associacao
sem inventar causa e o comportamento desejado. A limitacao entra na conclusao sempre que ha
ingrediente destacado.

`INGREDIENTES_NO_RANKING = 3`: a conclusao deterministica cita os tres primeiros, mandar dez ao
modelo era janela de contexto gasta com ingrediente que ninguem menciona.

## 8. Proximos passos identificados

### Resolvidos desde a primeira versao deste documento

- **Historico no payload da IA.** `RelatorioIAWorker.consumosDosDiasAnteriores` envia a serie do
  mesmo turno. Era o `List.of()` hardcoded do item 11 da RETROSPECTIVA.
- **Itens do cardapio no payload.** `itensDoCardapio` e `itensDoCardapioComTendencia` levam a taxa
  historica por item e a tendencia calculada no Python.
- **Previsao circular.** Resolvida na secao 4: piso no realizado do dia, com media historica e
  origem da estimativa expostas separadas.

### Abertos

- **Turmas sem denominador de presenca.** `porTurma` sai inteiro NAO_AVALIAVEL porque falta
  `alunos_elegiveis_dia`. Enquanto isso, a projecao da secao 5 resume o bloco em vez de mandar
  nove linhas iguais. Assim que o denominador existir, o detalhe volta sozinho.
- **Rotacao de cardapio.** `rotacaoCardapio` segue `DADOS_INSUFICIENTES`. Fadiga exige observar o
  declinio do mesmo prato ao longo do tempo; a serie por item ja existe (`serieDiariaDosItens`),
  falta a regra que decide quando a queda sustenta uma recomendacao de ciclo.
- **Motivo dos bloqueios agregado.** `tentativasBloqueadas` sozinho diz pouco: "sem matricula" e
  "ja consumiu hoje" sao situacoes opostas e hoje viram o mesmo numero.
- **`INTEGRAL` nao acumula consumo pela fila.** O `TurnoResolver` resolve pelo relogio da catraca
  e nunca devolve INTEGRAL, entao um cardapio INTEGRAL nao gera historico pelo fluxo real —
  justamente o turno mais usado em producao.
- **Cores dos cards do painel sao estaticas.** `Aceitacao` sempre verde e `Risco desperdicio`
  sempre ambar, independente do valor. Aceitacao BAIXA vai aparecer em verde.
- **Cadastro de cardapio ainda e campo de texto.** `GET /api/v1/receitas` existe, o front nao usa.
  Enquanto isso o nome digitado tem de bater exatamente com o catalogo, ou o cadastro devolve 400.
- **Payload voltou a crescer.** 6.838 caracteres depois do corte da projecao, 9.380 depois da
  analise por ingrediente. O corte do ranking para tres alivia; os candidatos seguintes sao
  `itensComMenorExecucao` (1.458) e `avisos` (807).
- **Saida estruturada do modelo trunca de vez em quando.** Uma execucao falhou com
  `Unexpected end-of-input: was expecting closing quote for a string value`. A tentativa seguinte
  passou. Se repetir, vale investigar limite de tokens de saida.
