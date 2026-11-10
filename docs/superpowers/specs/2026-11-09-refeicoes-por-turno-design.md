# Refeições por turno — desenho

Data: 2026-11-09. Repositórios: `api-merenda-escolar`, `analytics-merenda-escolar`, `front-merenda-escolar`.

## Intenção

Hoje o protótipo trata o turno como se fosse uma refeição: um cardápio por (data, turno), um fechamento por
(data, turno, item), uma leitura de fila sem saber qual refeição era. Isso não descreve a escola real. Pelo PNAE e
pelas redes de ensino, a quantidade de refeições depende do **regime da turma**: uma turma de período parcial
recebe uma ou duas (lanche no intervalo e, em algumas redes, outro na entrada ou saída); uma turma integral recebe
três ou mais (café da manhã, lanche, almoço, lanche da tarde). O próprio histórico do legado já separa assim
("Café de manhã — Turnos Integrais", "Almoço — Turnos Integrais", "Lanche da tarde — Turnos Integrais"), e a taxa de
execução muda muito de uma refeição para outra (de 20% a 41%), que é o sinal mais forte do conjunto.

O Frank quer o modelo certo: a refeição passa a ser a unidade do cardápio, da fila, do fechamento e da análise.

## Decisões do Frank

- Avaliar e desenhar a mudança completa, mesmo sendo grande.
- A fila descobre a refeição **pelo horário**, como hoje descobre o turno. A merendeira não escolhe a refeição.
- **Repetição continua permitida**: o bloqueio segue sendo só de 2 minutos, agora contado dentro da mesma refeição.
  Depois disso o aluno pode repetir, e a repetição fica registrada e entra na análise (o sintético continua
  simulando repetição).
- **A base local de demonstração pode ser recriada do zero** (apagar `data/merenda*`). Não há migração de dados de
  turma nem regeração de consumo antigo: o gerador de exemplos já nasce com turmas parciais e integrais e histórico
  por refeição. O rosto do Frank no ALU-0010 se perde e é cadastrado de novo.

## Metas

- Um aluno integral passa na fila no café, de novo no lanche e de novo no almoço, e cada passagem é autorizada e
  registrada na refeição certa. Na mesma refeição vale a regra de hoje: leitura repetida em menos de 2 minutos
  bloqueia; depois disso a repetição é autorizada e registrada.
- Um aluno de turma parcial só é autorizado nas refeições do seu período.
- O cadastro de cardápio é por refeição do dia; o fechamento mede a sobra de cada refeição.
- Aceitação e desperdício saem por refeição no Dashboard e no relatório de IA.
- Dados de demonstração com turmas parciais e integrais e cardápio de várias refeições.

## Fora de escopo

- Grade de refeições diferente por escola (o protótipo tem uma escola só).
- Cálculo nutricional, per capita do PNAE, contraturno com turma de outro período.
- Tela de cadastro da grade de refeições (vem pronta por migration; ver "Grade").
- Mudar a identificação (QR e facial seguem como estão; só o que acontece depois de identificar muda).

## Anti-metas

- Bloquear um aluno integral no almoço porque ele já comeu no café.
- Negar refeição por falha de configuração: se a grade estiver incompleta, a leitura bloqueia com motivo claro e
  orienta, nunca 500.
- Perder o histórico já gravado: cardápios, consumos e fechamentos antigos continuam legíveis, mapeados para uma
  refeição.

## Modelo (decidido pelo Claude, a validar pelo Frank na leitura)

### Regime vem do turno da turma

Não entra campo novo na turma. O turno que a turma já tem define o regime:
- `INTEGRAL` = turma de tempo integral;
- `MANHA`, `TARDE`, `NOITE` = turma parcial daquele período.

### Tipo de refeição (grade)

Entidade nova `TipoRefeicao` (tabela `tipo_refeicao`): nome, ordem, janela de horário (início e fim, hora local de
`America/Sao_Paulo`) e os turnos de turma que têm direito a ela. Grade inicial, semeada por migration:

| Refeição | Janela | Turnos com direito |
|---|---|---|
| Café da manhã | 07:00–08:00 | INTEGRAL |
| Lanche da manhã | 09:30–10:30 | MANHA, INTEGRAL |
| Almoço | 11:30–13:00 | INTEGRAL |
| Lanche da tarde | 15:00–16:00 | TARDE, INTEGRAL |
| Lanche da noite | 20:00–21:00 | NOITE |

Regra de consistência (testada): para um mesmo turno de turma, as janelas não se sobrepõem. Assim, dado o horário e
o turno da turma do aluno, existe no máximo uma refeição.

### Cardápio por refeição

`Cardapio` passa a ser por (data, tipo de refeição), único entre os ativos. O turno sai do cardápio (a refeição já
diz a quem serve). O cadastro de cardápio escolhe a refeição em vez do turno.

### Fila

Depois de identificar o aluno (QR ou facial, sem mudança):
1. Turma vigente do aluno na data → turno da turma. Aluno sem turma vigente: vale qualquer refeição cuja janela
   contenha o horário (mesmo espírito de hoje, que autoriza sem enturmação e carimba "Sem turma").
2. Refeição = a que contém o horário atual e dá direito ao turno da turma.
3. Sem refeição no horário: bloqueia com `FORA_DO_HORARIO_DE_REFEICAO`. Há refeição no horário, mas não para a turma
   do aluno: `REFEICAO_NAO_PREVISTA_PARA_TURMA`.
4. Cardápio da (data, refeição) ausente: `CARDAPIO_NAO_CONFIGURADO`, como hoje.
5. Leitura repetida bloqueada por (aluno, data, **refeição**) só dentro da janela de 2 minutos de hoje
   (`merenda.fila.bloqueio-repeticao`); fora dela, repetir é autorizado.
6. A auditoria grava a refeição (id e nome carimbado, como já faz com a turma).

O `TurnoResolver` sai da fila; quem resolve é um `RefeicaoResolver` sobre a grade.

### Fechamento

Medição de sobra por (data, refeição, escola, receita). A tela de Fechamento escolhe data e **refeição**.

### Análise, relatório e Dashboard

- A unidade de análise vira a refeição: o relatório de IA e o Dashboard são pedidos por (data de referência,
  refeição), no lugar de turno. A regra de amostra mínima (20 dias com fechamento nos últimos 30) passa a contar
  dias da refeição.
- O contrato Java → Python ganha a refeição; os indicadores por turma continuam, agora dentro de uma refeição.
  Versão do contrato sobe (hoje `schemaVersion=4`); relatórios antigos continuam abrindo.

### Migração dos dados existentes

A base local de demonstração é recriada (decisão do Frank), então não há conversão de dados de turma nem de
consumo. As migrations de schema ainda precisam deixar o banco válido para quem já tem dados, pelo mapeamento abaixo;
é rede de segurança, não caminho da demonstração.

- Cardápios, consumos (auditoria) e fechamentos antigos recebem a refeição por mapeamento do turno:
  MANHA → Lanche da manhã, TARDE → Lanche da tarde, NOITE → Lanche da noite, INTEGRAL → Almoço.
- Relatórios de IA antigos ficam como estão (fotografia do momento) e continuam legíveis.
- Histórico sintético do legado (`historico_consumo.refeicao`, texto): mapear os nomes do legado para os tipos de
  refeição; os que não têm correspondência clara ficam fora da análise por refeição, com aviso no relatório.

### Dados de demonstração

- Turmas parciais e integrais (por exemplo 1A–6A parciais da manhã/tarde, 7A–9A integrais).
- Cardápio do dia para cada refeição da grade, consumo sintético por refeição só para quem tem direito, e
  histórico de fechamento suficiente (≥ 20 dias) por refeição.
- As regras de ontem continuam: aluno com rosto fora do sintético, nada sintético no futuro, cardápio de hoje em
  todas as refeições.

### Relógio da demonstração (decisão do Claude)

A grade deixa vãos (por exemplo 08:00–09:30, 13:00–15:00, depois das 21:00), e fora das janelas a fila bloqueia com
`FORA_DO_HORARIO_DE_REFEICAO`. Para a apresentação e para a conferência na tela não dependerem da hora real, entra a
propriedade opcional `merenda.demo.horario` (ex.: `12:00`): quando definida, o `Clock` da aplicação usa a data real
com aquele horário. Sem ela, vale o relógio real. Fica documentada no README e no roteiro da demonstração.

### Outras decisões do Claude (a partir da revisão)

- Contrato Java → Python troca `turno` por `refeicao`; relatórios antigos continuam abrindo com o `turno` antigo.
- A previsão de consumo (`PythonPrevisaoConsumoAdapter`) também passa de turno para refeição.
- Relatórios antigos (`relatorio_ia.turno`) recebem a refeição mapeada para aparecerem na listagem por
  (data, refeição).
- Aluno sem turma diante de janelas sobrepostas de turnos diferentes: vale a de menor `ordem` na grade.
- Histórico antigo de Almoço (mapeado do INTEGRAL) não tem consumo, porque a fila nunca resolveu INTEGRAL; com a base
  recriada isso some da demonstração. Em bases antigas, a análise do Almoço considera só dias com consumo.

## Validação esperada

- Testes de domínio da grade (sem sobreposição por turno), do `RefeicaoResolver` (cada janela, fora de horário,
  sem direito, aluno sem turma), da fila (integral passa no café e no almoço; repetição na mesma refeição bloqueia;
  parcial barrado no almoço), da migração (dados antigos mapeados) e do contrato com o Python.
- Ponta a ponta com o relógio controlado nos testes; na tela, conferência com a skill `navegador`.

## Deixado para quem constrói

- Horários exatos da grade, desde que respeitem a regra de não sobreposição (os da tabela são o ponto de partida).
- Forma de guardar os turnos com direito (tabela de associação ou coluna).
- Mapeamento dos nomes de refeição do legado.
- Divisão em etapas e ordem das migrations; manter cada etapa com a suíte verde.
