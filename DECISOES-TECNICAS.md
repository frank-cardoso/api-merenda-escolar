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

## 4. Proximos passos identificados

Levantados durante a analise, fora do escopo desta branch:

- **Historico no payload da IA.** Hoje o modelo recebe um unico ponto de dado, por isso toda
  analise diz "historico insuficiente". Enviar os ultimos N dias do mesmo turno mudaria a
  qualidade da saida mais do que qualquer ajuste de prompt.
- **Itens do cardapio no payload da IA.** O cadastro agora produz itens estruturados, mas a
  analise ainda recebe apenas o nome da refeicao. Enviar os itens permitiria correlacoes do tipo
  "quando tem peixe, a aceitacao cai".
- **Motivo dos bloqueios agregado.** `tentativasBloqueadas` sozinho diz pouco: "sem matricula" e
  "ja consumiu hoje" sao situacoes opostas e hoje viram o mesmo numero.
- **Previsao do servico Python e circular sem historico.** O baseline devolve o consumo atual, e
  o LLM acaba repetindo as mesmas frases. So passa a agregar valor junto com o historico.
