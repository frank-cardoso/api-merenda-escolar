# Turma e aluno: entidade, vínculo com vigência e cadastros

Data: 2026-10-19
Status: aprovado, pronto para plano

## Intenção

Hoje o protótipo guarda `turma` como `String(80)` dentro de `Aluno`. É a mesma forma do
legado `api-merenda` (Java 8), onde turma é `VARCHAR2(80)` em `ALUNOS_MATRICULAS`, sem
tabela, sem id e sem CRUD.

Essa forma não sustenta duas coisas que o projeto quer:

1. **Reconhecimento facial** (próxima etapa, em Python com `deepface`/`face_recognition`,
   fora do caminho do LLM e sem custo de token). Ele precisa responder "quem é esse rosto e
   de que turma ele é", com o vínculo modelado de verdade.
2. **Apresentação do projeto integrador.** Turma e aluno precisam existir como cadastro
   visível para a banca entender a ideia.

No destino real, o vínculo aluno↔turma **não nasce aqui**: vem do `api-educacao`, que é
dono da criação de turma. O protótipo é vitrine. Por isso o modelo local deve ter a mesma
*forma* do núcleo do Educa, para que portar seja trocar a origem do dado sem mexer em quem
consome.

## Prior art

### Legado da merenda (`educacao/merenda/api-merenda`) — o que NÃO copiar

```
Aluno (VW_ALUNOS)
  ├─ pessoa → PessoaFisica (I_PESSOAS)
  ├─ identificacaoInep
  └─ alunoMatriculas → AlunoMatricula (VW_ALUNOS_MATRICULAS)
                        ├─ estabelecimento, dataAdmissao, etapa, situacao
                        ├─ turno  → enum Turno
                        └─ turma  → VARCHAR2(80)   ← string solta
```

A merenda não é dona desses dados: `ALUNOS` e `ALUNOS_MATRICULAS` são réplicas alimentadas
por integração (`COD_INTEGRACAO` / `VER_INTEGRACAO`, endpoints em `merenda-dados-war`),
multitenant por `I_DATABASES`, com as `VW_` filtrando o tenant.

### Núcleo do Educa (`educacao/educacao-jpa-modules`) — a forma a espelhar

- `Turma` (`VW_TURMAS`): `codigoTurma`, `descricao`, `turno`, `etapaEnsinoInep`,
  `quantidadeMaximaAlunos`, `quantidadeAlunos`, e muito mais que não interessa aqui.
- `Enturmacao` (`VW_ENTURMACOES`): liga `matricula` a `turma`, com `estabelecimento`,
  `anoLetivo`, `turnoFrequenta`, `numeroChamada`, `dataInicio`, `dataFim`.

O ponto que importa: **a ligação tem vigência**. Aluno troca de turma no meio do ano e a
enturmação anterior fecha com `dataFim`.

## Objetivos

- `Turma` vira entidade própria no protótipo, com os campos que a apresentação usa.
- Vínculo aluno↔turma por uma entidade de enturmação com `dataInicio` / `dataFim`.
- CRUD simples de aluno e de turma: API no Java e telas de cadastro no Angular.
- Indicadores por turma passam a usar **a turma que o aluno tinha na data do consumo**,
  não a turma atual do cadastro.
- Aluno e turma carregam um campo de código de origem (equivalente ao `COD_INTEGRACAO` do
  legado), hoje nulo, que amarrará o registro local ao id do Educa quando a integração
  entrar.

## Não-objetivos

- Reconhecimento facial em si. Este trabalho só prepara o terreno.
- Estoque. O legado resolve mal e, se um dia entrar, será refeito à parte.
- Multitenancy (`I_DATABASES`), `Estabelecimento` e `AnoLetivo` como entidades. O protótipo
  é uma escola, um recorte.
- Matrícula como entidade separada. O núcleo liga enturmação a `Matricula`; aqui a
  enturmação liga direto ao aluno.
- Mudanças em fila, cardápio, fechamento, medição de sobra, catálogo de receitas e nos
  cálculos de aceitação e desperdício.

## Anti-objetivos

Falha mesmo se funcionar:

- Entregar um `Turma` tão fiel ao núcleo que arraste ano letivo, matriz curricular,
  configuração de avaliação e vagas. A fidelidade que importa é a **da forma do vínculo**,
  não a contagem de campos.
- Encolher silenciosamente o total de Atendimentos. `IndicadoresDadosService` calcula
  `alunosUnicos` somando as turmas; consumo que não resolve turma some da soma sem erro,
  sem log e sem teste que pegue.
- Fazer o Python resolver turma. Ele recebe dado agregado e não lê domínio.
- Encher a tela de linhas `NAO_AVALIAVEL` com zero, perto demais de insinuar adesão baixa.

## Restrições

- Java 25 / Spring Boot 3.5 / H2 / Flyway.
- **O banco local é descartável.** A estrutura ainda não está definida e o H2 só existe
  para facilitar teste. Não há nada lançado à mão a preservar: o schema pode ser refeito do
  zero e `data/merenda.mv.db` pode ser apagado. Não há backfill a fazer — o
  `DadosDemonstracaoLoader` regenera o sintético já no formato novo.
- Angular 20 standalone + Tailwind, organizado por feature; uma feature não importa
  interno de outra.
- Python/FastAPI recebe só agregado, não persiste e não chama LLM.
- Números do dashboard nunca vêm da resposta do LLM.
- O payload do prompt é limitado por teto de tokens — ver `IndicadoresParaLLM`, que já
  resume turmas quando todas saem `NAO_AVALIAVEL`.
- `PythonIndicadoresLogisticosAdapter` rejeita resposta com `schemaVersion != "3"`
  (hard-coded) e o contrato do Python é `extra="forbid"`: Java e Python andam em lockstep.

## O que já está decidido

**Modelo.** `Turma` como entidade, com código, descrição e turno no mínimo. Vínculo por
entidade de enturmação com `dataInicio` e `dataFim`, espelhando `Enturmacao` do núcleo.
`Aluno` perde o campo `turma`. Enturmações do mesmo aluno não podem se sobrepor no tempo —
invariante com constraint no banco e validação no serviço.

**A turma é carimbada no consumo.** Quando a fila autoriza, grava junto a turma vigente
naquele instante. Combina com a postura que o projeto já tem (`indicadores_json` é
fotografia, relatório persistido não recalcula) e garante que o número de ontem continue o
mesmo na apresentação de hoje. Consequência aceita: corrigir uma enturmação lançada errada
não ajusta consumos já carimbados; na demo, o conserto é recarregar o banco.

**Aluno sem enturmação vigente cai num balde "Sem turma".** A fila autoriza normalmente —
regra de cadastro não entra no caminho crítico. O consumo aparece numa linha explícita no
dashboard, para o total de Atendimentos continuar batendo. Exige afrouxar
`ContagemTurma.turma`, hoje `str = Field(min_length=1)` em
`indicadores_logisticos.py:15`, que rejeitaria vazio com 422 e derrubaria o dashboard
inteiro em `INDISPONIVEL`. Com o loader cobrindo todo o período, esse é caso de borda
(aluno cadastrado durante a demo), não regra.

**Front: transferência com data, mais histórico de vínculos.** Não é um `select` de turma
na tela do aluno. É uma ação própria — escolhe turma nova, informa a data da mudança — e a
tela do aluno lista os vínculos. É o que torna a vigência visível para a banca: dá para
mostrar o aluno saindo da 7A em julho, entrando na 8A em agosto, e o histórico de agosto
continuando na turma certa. Com `select` implícito não há como distinguir correção de
transferência.

**Contrato com o Python.** `ContagemTurma` ganha identificação de turma; o nome segue como
rótulo. `schemaVersion` sobe para `4`, como já foi feito de 2 para 3, e o `3` hard-coded no
adapter acompanha. No modelo do front o id de turma entra como opcional, para os
`indicadores_json` já persistidos com `schemaVersion` 2 e 3 continuarem abríveis. O motivo
fixo *"Sem registro de presença elegível; turma atual do cadastro do aluno."*
(`indicadores_logisticos.py:171`) deixa de ser verdade e é reescrito.

**`porTurma` segue listando só turmas que tiveram atendimento**, como o README já diz. Com
`Turma` virando entidade o reflexo seria listar todas; isso infla o payload do prompt (o
bloco de turmas já era o maior) e cai no anti-objetivo das linhas zeradas.

**`turno` da `Turma` é rótulo de cadastro.** Não filtra nem restringe o agrupamento por
`data + turno` que já existe.

**Adesão por turma continua `NAO_AVALIAVEL`.** Enturmação não é presença elegível. Ter
turma modelada não autoriza calcular adesão — essa pendência segue aberta nos três READMEs.

## O que fica com quem for implementar

- O subconjunto exato de campos de `Turma`. O mínimo defensável é código, descrição e
  turno; `quantidadeMaximaAlunos` ajuda a apresentação a falar de capacidade.
- Refazer o schema num baseline limpo ou acrescentar uma `V10`. O banco é descartável, então
  é escolha de clareza, não de risco.
- Como o `DadosDemonstracaoLoader` passa a gerar turmas e enturmações cobrindo todo o
  período do histórico sintético, incluindo ao menos um aluno que troca de turma no meio do
  intervalo — sem ele a tela de transferência não tem o que demonstrar.
- As 9 turmas sintéticas de hoje (`"%dA"`, de 1A a 9A, em `garantirAlunos`) viram uma
  `Turma` por valor distinto; decidir o turno de cada uma.
- Tipo de id. O protótipo usa `UUID` em `Aluno`; manter o padrão.
- Se a leitura de aluno com turma vigente (usada depois pelo reconhecimento facial) entra
  já no CRUD. Hoje o pacote `aluno/` só tem entidade e repositório: o
  `GET|POST|PUT /api/v1/alunos` está no `implementation_plan.md:58` mas não existe
  controller algum.
