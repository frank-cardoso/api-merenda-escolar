# Grade de refeições visível e editável — desenho

Data: 2026-11-10. Repositórios: `api-merenda-escolar`, `front-merenda-escolar`. Continua
`2026-11-09-refeicoes-por-turno-design.md` (refeição como unidade) e `2026-11-09-painel-fechamento-design.md`.

## Intenção

O Frank notou que o vínculo entre refeição e turma não fica claro: a turma guarda só o turno, a grade (que refeição
vale para que turno) nasce pela migration V14 e nenhuma tela a mostra. Quem cadastra uma turma "Manhã" não sabe que
ela só terá o lanche da manhã; quem é barrado na fila lê "refeição não prevista para a turma" sem saber qual refeição
nem para quem ela vale; e mudar a grade exige migration.

### O que a pesquisa mostrou

- **Brasil (PNAE, Resolução CD/FNDE 06/2020):** o número de refeições depende do **tempo de permanência** (parcial ou
  integral) e da **etapa/modalidade**: parcial com 1 refeição = 20% das necessidades diárias, parcial com 2+ = 30%,
  integral = no mínimo 3 refeições e 70%. O cardápio é planejado pelo nutricionista por modalidade. **Cada rede monta
  a grade:** São Paulo serve lanche + almoço + lanche no integral; Curitiba, café + almoço + lanche da tarde, e uma
  refeição para quem estuda meio período. Há **exceções**, como o "lanche contraturno" de Campinas.
- **Fora do Brasil:** EUA oferecem café e almoço na escola, com padrão de cardápio por faixa de série (K-5, 6-8, 9-12)
  e gratuidade por renda; Inglaterra garante almoço gratuito aos três primeiros anos (Reception–Year 2) e depois por
  renda; Finlândia serve almoço a todos.
- **Conclusão:** em nenhum lugar a refeição é ligada turma a turma. O direito sai de uma **regra por categoria**
  (regime/tempo de permanência no Brasil; série ou renda lá fora), sobre uma **grade definida pela rede**. O modelo do
  protótipo (turno da turma → refeições da grade) está alinhado; o que falta é a regra **visível** e a grade
  **configurável**. Exceção por turma existe, mas é caso especial.

## Decisões (o Frank pediu para seguir com a melhor abordagem a partir da pesquisa)

- A: mostrar o vínculo em todo lugar onde ele importa.
- B: grade editável numa tela própria.
- C (direito por turma / contraturno): fora, registrado como próximo passo.

## Metas

- Ver a grade inteira numa tela: refeição, janela e turnos com direito.
- Na tela de Turmas, ver a que refeições cada turma tem direito; no formulário, ao escolher o turno, ver o efeito.
- Na fila, o bloqueio por turma diz qual refeição está na janela e para quais turnos ela vale.
- Criar refeição e editar nome, janela e turnos com direito, com validação clara.

## Fora de escopo

- Direito por turma (exceção ao turno), contraturno, regime separado do turno.
- Excluir refeição. Para tirar uma refeição de uso, deixá-la sem turnos (fica visível, ninguém tem direito).
- Grade diferente por escola, por etapa ou por dia da semana.
- Histórico/versão da grade.

## Anti-metas

- Editar a grade reescrever o passado: consumos, cardápios, fechamentos e relatórios já gravados continuam ligados à
  refeição (id) e com o nome carimbado onde já há carimbo.
- Grade inválida gravada: sobreposição de janelas para o mesmo turno, início ≥ fim ou nome repetido nunca entram.
- Erro 500 em qualquer validação da grade: sempre 4xx com mensagem em português.

## API

### `GET /api/v1/tipos-refeicao` (já existe)

Continua igual: `[{ id, nome, ordem, inicio, fim, turnos }]`, ordenado por `ordem`.

### `POST /api/v1/tipos-refeicao` e `PUT /api/v1/tipos-refeicao/{id}`

Corpo `{ nome, inicio: "HH:mm", fim: "HH:mm", turnos: ["MANHA", …] }`. Validação:
- `nome` obrigatório, até 60, único sem diferenciar maiúsculas → 409 se repetido;
- `inicio < fim` → 422 "O início precisa ser antes do fim.";
- a grade resultante passa por `GradeRefeicoes.de` (sem sobreposição para o mesmo turno) → 422 com a mensagem do
  domínio ("As refeições X e Y se sobrepõem para o mesmo turno.");
- `turnos` pode ser vazio;
- `PUT` de id inexistente → 404.

`ordem` não vem do cliente: depois de salvar, as refeições são renumeradas pelo `inicio` (1, 2, 3…). Respostas: 201
com o `TipoRefeicaoResponse` no POST, 200 no PUT.

### Fila: bloqueio por turma explicado

Quando o motivo é `REFEICAO_NAO_PREVISTA_PARA_TURMA`, a resposta passa a trazer `tipoRefeicaoId` e `refeicao` da
refeição que está na janela (a de menor ordem, se houver mais de uma) e dois campos novos, presentes em toda resposta
e nulos quando não se aplicam: `turnoDaTurma` (ex.: `"MANHA"`) e `turnosDaRefeicao` (ex.: `["INTEGRAL"]`). O resto do
contrato não muda.

## Front

- **Rótulos de turno** num lugar só: MANHA "Manhã", TARDE "Tarde", NOITE "Noite", INTEGRAL "Integral".
- **Função compartilhada** `refeicoesDoTurno(grade, turno)`: refeições cujo `turnos` contém o turno, na ordem da grade.
- **Turmas:** coluna "Refeições" com selos dos nomes (sem turno com direito: "Nenhuma refeição"); no formulário, sob o
  select de turno, a linha "Esta turma terá direito a: Café da manhã, Lanche da manhã, …" que muda com o turno.
- **Grade de refeições:** nova rota `/grade-refeicoes`, item "Refeições" no menu. Tabela com nome, janela e turnos
  (selos), botão "Nova refeição" e "Editar" por linha; formulário com nome, início, fim (inputs `time`) e quatro
  caixas de turno. Erros 409/422 mostram a mensagem da API. Texto de apoio curto: a grade define quem tem direito a
  cada refeição pelo turno da turma, e mudanças valem daqui para frente.
- **Fila:** o detalhe do bloqueio por turma vira "{refeicao} é para turmas {turnos}; a turma do aluno é {turno}." (ex.:
  "Almoço é para turmas Integral; a turma do aluno é Manhã."). Sem os campos novos, continua o texto atual do motivo.

## Dados de demonstração e migração

Nada muda na grade semeada. Sem migration nova (a tabela já tem tudo).

## Decisões do Claude

- `ordem` derivada do início; sem exclusão; turnos vazios permitidos.
- Nome único sem diferenciar maiúsculas (409).
- Campos `turnoDaTurma`/`turnosDaRefeicao` na resposta da fila em vez de montar texto no backend.

## Validação esperada

- API: criar, editar, sobreposição (422), início ≥ fim (422), nome repetido (409), id inexistente (404), renumeração
  da ordem, fila com bloqueio por turma trazendo os campos novos; histórico intacto após editar nome.
- Front: coluna e linha de direito nas Turmas; tela da grade lista, cria, edita e mostra erro da API; detalhe da fila.
- Tela: editar a janela de uma refeição e ver o painel do Fechamento e a fila respeitarem a mudança.

## Fontes

- CRN-3, planejamento de cardápio e legislação do PNAE: https://crn3.org.br/arquivos/daniela-bicalho-planejamento-de-cardapio-desmistificando-a-legislacao-do-pnaepdf.pdf
- FNDE, normas para creche: https://www.gov.br/fnde/pt-br/acesso-a-informacao/acoes-e-programas/programas/pnae/media-pnae/NormasGeraisparaatendimentodosestudantesdacreche.pdf
- Prefeitura de São Paulo: https://prefeitura.sp.gov.br/noticia/prefeitura-serve-2-3-milhoes-de-refeicoes-diariamente-para-mais-de-1-milhao-de-alunos-na-cidade
- Prefeitura de Curitiba: https://www.curitiba.pr.gov.br/noticias/escolas-e-cmeis-de-curitiba-servem-300-mil-refeicoes-por-dia-com-acompanhamento-nutricional/77728
- Campinas, lanche contraturno: https://educa.campinas.sp.gov.br/sites/educa.campinas.sp.gov.br/files/alimentacao-escolar/04-2024/lanche-contraturno-ensino-fundamental_2.pdf
- USDA, padrão do café da manhã escolar: https://www.fns.usda.gov/school-meals/nutrition-standards/sbp-meal-pattern
- Connecticut, padrões por faixa de série: https://portal.ct.gov/SDE/Nutrition/Meal-Patterns-School-Nutrition-Programs
- GOV.UK, Universal Infant Free School Meals: https://www.gov.uk/government/publications/universal-infant-free-school-meals-uifsm-2026-to-2027/universal-infant-free-school-meals-uifsm-conditions-of-grant-2026-to-2027
- Finlândia: https://educationfinland.fi/school-meals/finnish-model ; FAO: https://www.fao.org/platforms/school-food/around-the-world/europe-and-central-asia/finland/en
