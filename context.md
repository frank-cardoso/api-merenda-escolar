# Contexto Arquitetural — Backend

## Objetivo

Esta API nasceu como um prototipo standalone para o Projeto Integrador, mas sua logica deve ser facil de transportar para um sistema legado corporativo em Java.

A decisao arquitetural e manter a API como um monolito modular por capacidade de negocio, separando regra de aplicacao, contratos HTTP, persistencia e integracoes externas.

## Direcao de evolucao

Controllers REST devem ser apenas uma camada de entrada. A regra aproveitavel deve ficar em services de aplicacao e portas.

Os principais pontos portaveis sao:

- `ValidacaoConsumoService`: regra de validacao da fila e bloqueio de consumo duplicado.
- `ConsolidacaoConsumoService`: consolidacao deterministica dos logs de consumo.
- `RelatorioIAService`: criacao e consulta do job de relatorio.
- `RelatorioIAWorker`: processamento assicrono do relatorio.
- `AnaliseLogisticaPort`: contrato de integracao com provedor de IA.

## Estrutura esperada

```text
br.com.frankcardoso.merenda
├── aluno
├── cardapio
├── fila
├── gestao
├── inteligencia
├── relatorio
└── shared
```

Cada modulo deve preferir:

- `api`: controllers e DTOs HTTP.
- `application`: casos de uso e regras de orquestracao.
- `domain`: entidades, enums e regras internas.
- `infrastructure`: JPA, adapters externos e detalhes tecnicos.

## Regras para facilitar migracao

- A fila nunca deve depender de IA.
- A IA deve receber somente dados agregados, nunca aluno, matricula, foto ou biometria.
- Regras de negocio importantes devem ficar fora dos controllers.
- Integracoes externas devem ser acessadas por portas, como `AnaliseLogisticaPort`.
- DTOs e contratos imutaveis podem usar `record`.
- Entidades JPA devem continuar como classes.
- O modelo Gemini deve ser configuravel por ambiente, via `GEMINI_MODEL`.

## Estrategia recomendada

Manter tudo neste repositorio durante o prototipo. Extrair para um modulo separado, como `merenda-core-java`, somente quando o legado realmente precisar consumir a logica como biblioteca interna ou modulo compartilhado.
