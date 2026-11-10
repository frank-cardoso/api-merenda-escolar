# Painel do dia no Fechamento — desenho

Data: 2026-11-09. Repositórios: `api-merenda-escolar`, `front-merenda-escolar`. Continua
`2026-11-09-refeicoes-por-turno-design.md` (a refeição já é a unidade do fechamento).

## Intenção

Com o fechamento por refeição, quem fecha (a merendeira) escolhe data e refeição, clica em "Carregar refeição" e só
então descobre se aquela refeição já foi fechada. Não existe uma visão do dia: o que foi servido, o que falta fechar,
o que ainda nem começou. O Frank perguntou "como eu sei o que fechei?" e aprovou o painel do dia, a abertura na
pendência e o aviso no Dashboard.

As referências de alimentação escolar confirmam a unidade e o momento: o registro de produção é feito **por
refeição servida, no mesmo dia, logo depois de servir**, porque a precisão cai com o tempo (USDA/ISBE, South Dakota,
Connecticut; registro canadense de comida não servida; guia do RIT anota o tipo de refeição). Os estudos brasileiros
separam sobra limpa e resto-ingestão, como o fechamento já faz. Um fechamento esquecido também vira buraco no
histórico que a análise de IA exige (20 dias com fechamento por refeição).

## Metas

- No Fechamento, ver de relance a situação de cada refeição do dia escolhido e ir direto à que precisa de ação.
- A tela abre na refeição pendente mais antiga do dia, não na primeira da grade.
- Quem faz a gestão vê no Dashboard quantas refeições ficaram sem fechamento nos últimos 7 dias e chega a cada uma
  com um clique.

## Fora de escopo

- Medir o resto do prato só 1–2 dias por semana (amostragem, sugerida pelas referências canadenses). Mexe no modelo
  (`restoNoPrato` obrigatório) e no cálculo de aceitação do Python; fica registrado como próximo passo.
- Notificação ativa (e-mail, push), permissões por perfil, assinatura de quem fechou.
- Mudar a regra de quando um fechamento é aceito (continua: um registro por item do cardápio).

## Anti-metas

- Bloquear o fechamento fora da janela: fechar antes do fim da refeição ou dias depois continua permitido.
- O painel inventar pendência em dia sem cardápio, ou em refeição cuja janela ainda não acabou.
- O aviso do Dashboard ficar ruidoso na demonstração com pendências que o próprio gerador de exemplos criou.

## Situação de uma refeição num dia

Para (data, refeição), com a hora de agora no fuso `America/Sao_Paulo` e o `Clock` da aplicação (vale o relógio de
demonstração `merenda.demo.horario`):

| Situação | Quando |
|---|---|
| `SEM_CARDAPIO` | não há cardápio ativo para (data, refeição) |
| `FECHADA` | há cardápio e todo item (receita) dele tem medição para a data e a refeição |
| `AGUARDANDO` | não fechada, e a janela ainda não começou (data futura, ou hoje antes do início) |
| `EM_ANDAMENTO` | não fechada, hoje, dentro da janela `[início, fim)` |
| `PENDENTE` | não fechada, e a janela já acabou (data passada, ou hoje a partir do fim) |

A ordem de avaliação é a da tabela: `SEM_CARDAPIO` primeiro, depois `FECHADA`; fechar antes do fim da janela conta
como fechada. Fechamento parcial (alguns itens medidos) não é fechada: aparece como a situação da janela, com a
contagem de itens medidos à vista.

## API

### `GET /api/v1/fechamentos/dia?data=AAAA-MM-DD`

Uma linha por refeição da grade, na ordem da grade (inclusive as que não têm turma na escola, como o Lanche da noite):

```json
[{
  "tipoRefeicaoId": "5e1a0000-0000-4000-8000-000000000002",
  "refeicao": "Lanche da manhã", "ordem": 2, "inicio": "09:30", "fim": "10:30",
  "situacao": "PENDENTE",
  "cardapioId": "…", "itensCardapio": 5, "itensMedidos": 0,
  "consumosAutorizados": 36,
  "fechadoEm": null
}]
```

`fechadoEm` é o maior `registradoEm` das medições quando `FECHADA`, senão `null`. `cardapioId` é `null` em
`SEM_CARDAPIO`. `data` obrigatória (400 sem ela).

### `GET /api/v1/fechamentos/pendencias?ate=AAAA-MM-DD&dias=7`

Refeições em `PENDENTE` nos `dias` dias que terminam em `ate` (inclusive), ordenadas por data e ordem da grade:
`[{ "data", "tipoRefeicaoId", "refeicao", "itensCardapio", "itensMedidos" }]`. `dias` entre 1 e 31, padrão 7; fora
disso, 400. Dias sem cardápio não entram (a regra de `SEM_CARDAPIO` já os exclui).

A lógica da situação fica num lugar só (serviço de domínio do fechamento) e os dois endpoints a usam. Consultas por
intervalo de datas em vez de uma por (dia, refeição) quando der.

## Front

### Fechamento

- Acima do formulário, faixa "Refeições do dia" com um cartão por refeição da data escolhida: nome, janela, situação
  (selo com cor: pendente em âmbar, em andamento em azul, fechada em verde com "Fechada às HH:mm", aguardando e sem
  cardápio em cinza), alunos que passaram na fila e itens medidos/total quando houver cardápio.
- Clicar num cartão com cardápio seleciona a refeição e carrega o formulário (o mesmo efeito de escolher no select e
  clicar em "Carregar refeição"). Cartão `SEM_CARDAPIO` não é clicável.
- Ao abrir a tela (e ao trocar a data), a refeição selecionada é: a primeira `PENDENTE` na ordem da grade; senão a
  `EM_ANDAMENTO`; senão a primeira da grade. Parâmetros de rota `?data=AAAA-MM-DD&refeicao=<tipoRefeicaoId>`, quando
  vierem, mandam na escolha.
- Depois de fechar ou salvar a edição, o painel recarrega.

### Dashboard

- Aviso "Fechamentos pendentes" na aba Indicadores, consultando `pendencias` com `ate` = data de referência do filtro
  e `dias=7`: some quando a lista está vazia; com itens, mostra "N refeições sem fechamento nos últimos 7 dias" e a
  lista "dd/mm • Refeição", cada uma com link para `/fechamento?data=…&refeicao=…`. Falha ao consultar não derruba o
  Dashboard (o aviso não aparece).

## Dados de demonstração

O gerador deixava o fechamento de hoje do Almoço já feito no boot. Com o painel, isso mostraria o Almoço "Fechada" no
meio do almoço. Passa a não gerar fechamento de hoje para nenhuma refeição (dias anteriores continuam todos fechados).
Assim, às 12:00 da demonstração o painel mostra Café e Lanche da manhã pendentes, Almoço em andamento, Lanche da
tarde e Lanche da noite aguardando, e o Dashboard avisa 2 pendências.

## Decisões do Claude (a conferir na leitura)

- Situações e precedência da tabela acima; fechamento parcial não conta como fechado.
- Painel mostra todas as refeições da grade, mesmo sem turma; o aviso só conta dias com cardápio.
- Janela do aviso: 7 dias até a data de referência do Dashboard.
- Fim da geração do fechamento de hoje do Almoço na demonstração.
- Amostragem do resto do prato fica fora (motivo acima).

## Validação esperada

- Testes de domínio da situação (cada linha da tabela, borda início inclusiva e fim exclusivo, data futura, fechada
  antes do fim, parcial).
- Testes HTTP dos dois endpoints (400 sem `data`, `dias` fora da faixa).
- Front: painel renderiza situações; seleção inicial (pendente > em andamento > primeira); clique carrega; parâmetros
  de rota; aviso do Dashboard com e sem pendências, e falha silenciosa.
- Ponta a ponta com o relógio de demonstração em 12:00 e conferência na tela.

## Fontes

- ISBE, Menu Recordkeeping: https://www.isbe.net/Documents/061725MenuRecordkeeping.pdf
- South Dakota, Production Record Handbook: https://doe.sd.gov/CANS/documents/ProductionRecordHandbook-25.pdf
- Connecticut, Production records: https://portal.ct.gov/sde/nutrition/production-records-for-school-nutrition-programs
- School Food in Canada, kitchen waste log e plate waste: https://spheru.ca/documents/school-food-resources/
- RIT, Foodservice Best Practices: https://www.rit.edu/affiliate/nysp2i/sites/rit.edu.affiliate.nysp2i/files/food-waste-toolkit/Foodservice_Best_Practices_2020.pdf
- UNESP, sobra e resto-ingestão: https://repositorio.unesp.br/server/api/core/bitstreams/2264fc7a-f300-4282-828f-bb335eb2f9a5/content
