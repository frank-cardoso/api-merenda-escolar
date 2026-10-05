# Fechamento do turno — desenho

## Problema

A medição de sobra não tem interface. Existe endpoint (`POST /api/v1/medicoes-sobra`), faker e
cálculo, mas lançar uma medição só é possível por `curl`. Como aceitação e desperdício dependem
dela, os dois indicadores ficam permanentemente `NAO_AVALIAVEL` em uso real.

Há ainda um efeito de tela: o painel não mostra nenhum indicador de perda enquanto ninguém mede,
mesmo tendo um que é calculável sempre — a sobra de planejamento.

E o vínculo entre as telas é invisível: quem bipa um QR na Fila não vê aquele consumo chegar em
lugar nenhum.

## Objetivo

Fechar o ciclo do turno sem `curl`: cardápio → consumo na fila → medição → relatório.

## O que entra

### 1. Tela `Fechamento do turno` (nova rota `/fechamento`)

Quarto item do menu. Seleciona data e turno, carrega o cardápio e os consumos, e apresenta uma
linha por item com quatro campos.

Todos os campos chegam **pré-preenchidos** com o que o sistema já sabe: `porcoesPreparadas` e
`porcoesServidas` recebem a contagem de consumos autorizados do turno; `sobraNaoDistribuida` e
`restoNoPrato` começam em zero. Quem fecha o turno só altera o que sobrou.

Isso é deliberado. A regra de cobertura exige medição de todos os itens do cardápio, e sem
pré-preenchimento isso seriam dezesseis campos digitados num cardápio de quatro itens. A resposta
não é afrouxar a regra, é não pedir o que já se sabe.

Dois botões: `Não sobrou nada`, que confirma os zeros, e `Fechar turno e gerar relatório`, que
lança as medições e cria o relatório na sequência.

Relançar sobrescreve — o endpoint já se comporta assim, porque medição é observação e não evento.

### 2. Separação dos cards no Dashboard

Hoje existe um card `Risco de desperdício` que fica apagado sem medição, e o número de sobra de
planejamento aparece solto como "Planejado sem consumo registrado".

Passam a ser três, lado a lado:

| Card | Origem | Disponibilidade |
|---|---|---|
| Sobra de planejamento | planejado menos registrado | sempre |
| Aceitação | medição de resto no prato | com medição |
| Desperdício medido | medição de sobra e resto | com medição |

Sobra de planejamento continua **não** sendo desperdício de alimento: pode ser que a cozinha nem
tenha preparado o planejado. A separação existe justamente para não confundir os dois, mantendo
visível o indicador que o QR sozinho sustenta.

### 3. Contador na Fila

`hoje: N consumos`, incrementado a cada leitura aprovada. Torna visível que o bip alimenta o
número do painel, em vez de exigir que alguém afirme isso.

## O que não entra

- **Relatório por cardápio.** Boa ideia, mas amplia antes do ciclo fechar.
- **Afrouxar a cobertura para medição parcial.** Com pré-preenchimento o custo de preencher tudo é
  próximo de zero; cobertura parcial deve continuar barrando, porque aí falta informação de fato.
- **Campo em quilos.** Pesagem é o método rigoroso e o legado tem fator de conversão
  (`CONVERSOES_UNIDADES`, `Ingrediente.porcao`), mas no protótipo a contagem por porções resolve.

## Back-end

Nenhuma mudança. A tela usa endpoints existentes:

- `GET /api/v1/cardapios` — itens com `receitaId` e nome
- `GET /api/v1/gestao/consolidacoes` — consumos autorizados do turno
- `POST /api/v1/medicoes-sobra` — uma chamada por item
- `POST /api/v1/relatorios-ia` — gera o relatório

## Testes

- Pré-preenchimento a partir do cardápio e da consolidação
- `Não sobrou nada` mantém os zeros e habilita o fechamento
- Fechamento dispara uma medição por item e depois o relatório
- Falha em uma medição interrompe antes de gerar o relatório, com mensagem
- Cards do Dashboard: sobra de planejamento visível sem medição; os outros dois com o rótulo de
  indisponibilidade quando o indicador vier `NAO_AVALIAVEL`
