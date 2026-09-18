# Plano de Implementacao — Controle de Merenda Escolar

## Arquitetura

O sistema e formado por dois repositorios: uma API Spring Boot e um frontend Angular. A API e um monolito modular organizado por capacidade de negocio: `aluno`, `cardapio`, `fila`, `relatorio`, `inteligencia` e `shared`.

Existem dois fluxos deliberadamente separados:

1. A fila e sincrona, transacional e nunca acessa o LLM.
2. A gestao consolida dados e cria jobs assincronos para analise pelo LLM.

A integracao Gemini fica na API atras de `AnaliseLogisticaPort`, com adapters `fake` e `gemini`. O modelo configurado por padrao no prototipo e `gemini-3.6-flash`, com override por `GEMINI_MODEL`. Um servico separado so deve ser considerado quando houver necessidade comprovada de deploy, equipe ou escala independentes.

## Modelo inicial

- `Aluno`: UUID, codigo publico do QR, matricula, nome, turma e situacao.
- `BiometriaFacial`: descriptor facial, versao do modelo e registro de consentimento; fotografias nao sao persistidas.
- `Cardapio`: data, turno, itens e quantidade planejada.
- `AuditoriaConsumo`: aluno, cardapio, turno, metodo, resultado, motivo e correlation ID.
- `RelatorioIA`: periodo, status persistido, entrada agregada, resultado, modelo e erro.

DTOs e contratos imutaveis usam records. Entidades JPA permanecem classes. Datas operacionais usam `LocalDate`, instantes usam `Instant` e enums sao persistidos como texto.

## Contratos REST

- `POST /api/v1/fila/validacoes`: autoriza ou bloqueia o consumo.
- `GET|POST|PUT /api/v1/alunos`: cadastro e consulta.
- `PUT /api/v1/alunos/{id}/biometria`: cadastro do descriptor.
- `GET|POST|PUT /api/v1/cardapios`: gestao do cardapio.
- `GET /api/v1/consumos`: consulta da auditoria.
- `GET /api/v1/gestao/consolidacoes`: indicadores deterministas.
- `POST /api/v1/relatorios-ia`: cria job e retorna HTTP 202.
- `GET /api/v1/relatorios-ia/{id}`: consulta status e resultado.
- `POST /api/v1/relatorios-ia/{id}/reprocessamentos`: reprocessa falhas.

Bloqueios de negocio retornam HTTP 200 com sinal vermelho. Entradas invalidas e falhas tecnicas usam `ProblemDetail`.

## Fases

1. Scaffolding, H2, Flyway, OpenAPI e dados fake.
2. Regra transacional de bloqueio temporario por aluno no turno e fila com QR Code.
3. Cadastro e reconhecimento facial local no navegador.
4. Consolidacao deterministica e dashboard gerencial.
5. Job persistido, adapter fake e integracao Gemini free tier. **Fundacao implementada:** consolidacao, estados do job, porta de IA, adapter fake e adapter Gemini com saida estruturada.
6. Testes de concorrencia, privacidade, acessibilidade e documentacao final.

## Criterios de aceite

- A primeira leitura autoriza, uma repeticao dentro de 2 minutos no mesmo turno bloqueia e uma nova leitura apos a janela configurada autoriza novamente.
- Requisicoes concorrentes produzem exatamente um consumo autorizado.
- QR e face usam o mesmo endpoint e a mesma regra.
- Nenhuma indisponibilidade da IA afeta a fila.
- O LLM recebe somente dados agregados.
- O dashboard reproduz os dados persistidos.
- Ambos os projetos compilam e podem ser executados seguindo seus READMEs.
