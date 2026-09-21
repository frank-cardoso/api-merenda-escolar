# Plano de Implementacao — Controle de Merenda Escolar

## Arquitetura

O sistema e formado por dois repositorios: uma API Spring Boot e um frontend Angular. A API e um monolito modular organizado por capacidade de negocio: `aluno`, `cardapio`, `fila`, `relatorio`, `inteligencia` e `shared`.

Existem dois fluxos deliberadamente separados:

1. A fila e sincrona, transacional e nunca acessa o LLM.
2. A gestao consolida dados e cria jobs assincronos para analise pelo LLM.

A integracao Gemini fica na API atras de `AnaliseLogisticaPort`, com adapters `fake` e `gemini`. O modelo configurado por padrao no prototipo e `gemini-3.6-flash`, com override por `GEMINI_MODEL`. Um servico separado so deve ser considerado quando houver necessidade comprovada de deploy, equipe ou escala independentes.

Aos modulos originais somaram-se `historico` (serie sintetica no formato do legado), `medicao` (sobra medida e composicao das receitas) e `analytics` (porta para o servico Python).

O que o LLM recebe nao e o mesmo objeto que a tela le: `IndicadoresParaLLM` projeta a fotografia para o prompt, enquanto `relatorio_ia.indicadores_json` guarda o detalhe completo. E o codigo deterministico que fecha a conclusao numerica, em `ConclusaoDeterministica`; o modelo redige a partir dela e nao vota em `nivelAceitacao` nem `riscoDesperdicio`.

Item e identificado por `receita.id`, nunca por nome — nome e rotulo de exibicao. O servico Python recebe o grafo receita -> ingrediente no proprio pedido e nao e dono de tabela de dominio nenhuma: e isso que o torna a unica peca portavel para outra base sem alteracao.

O porque de cada uma dessas decisoes esta em `DECISOES-TECNICAS.md`.

## Modelo inicial

Persistidos hoje:

- `Aluno`: UUID, codigo publico do QR, matricula, nome, turma e situacao.
- `Cardapio`: data, turno, itens e quantidade planejada. Soft delete preserva a auditoria.
- `AuditoriaConsumo`: aluno, cardapio, turno, metodo, resultado, motivo e correlation ID.
- `RelatorioIA`: periodo, status persistido, entrada agregada, resultado, modelo, erro e a fotografia de indicadores do momento da geracao.
- `Receita`: item que pode ir ao cardapio, com id proprio. `codigo_externo` reservado para o `RECEITAS.ID` do legado.
- `Ingrediente`: id proprio, equivalente a `INGREDIENTES.ID` do legado.
- `ReceitaIngrediente`: composicao, por id nos dois lados. Base da analise de aceitacao por ingrediente.
- `HistoricoConsumo`: um registro por (data, turno, escola, refeicao, receita), no mesmo grao do legado. Sintetico, gerado por `scripts/gerar_historico_fake.py`.
- `MedicaoSobra`: sobra medida por (data, turno, escola, receita), em porcoes. Guarda `sobra_nao_distribuida` e `resto_no_prato` separados — so a segunda mede rejeicao.

Previsto no desenho, ainda nao implementado:

- `BiometriaFacial`: descriptor facial, versao do modelo e registro de consentimento; fotografias nao seriam persistidas.

DTOs e contratos imutaveis usam records. Entidades JPA permanecem classes. Datas operacionais usam `LocalDate`, instantes usam `Instant` e enums sao persistidos como texto.

## Contratos REST

Implementados:

- `POST /api/v1/fila/validacoes`: autoriza ou bloqueia o consumo.
- `GET|POST|PUT|DELETE /api/v1/cardapios`: gestao do cardapio.
- `GET /api/v1/receitas`: catalogo, para o cadastro de cardapio escolher receita por id em vez de digitar nome.
- `POST /api/v1/medicoes-sobra`: lanca a sobra medida de uma receita ao fechar o turno. Recebe `receitaId`. Relancar a mesma receita no mesmo dia e turno sobrescreve.
- `GET /api/v1/gestao/consolidacoes`: consolidacao do dia.
- `GET /api/v1/gestao/indicadores`: indicadores deterministas.
- `POST /api/v1/relatorios-ia`: cria job e retorna o id.
- `GET /api/v1/relatorios-ia/{id}`: consulta status e resultado.
- `GET /api/v1/relatorios-ia?data=&turno=`: relatorios salvos do dia e turno.

Previstos no desenho, ainda nao implementados:

- `GET|POST|PUT /api/v1/alunos`: cadastro e consulta.
- `PUT /api/v1/alunos/{id}/biometria`: cadastro do descriptor.
- `GET /api/v1/consumos`: consulta da auditoria.
- `POST /api/v1/relatorios-ia/{id}/reprocessamentos`: reprocessa falhas.

Bloqueios de negocio retornam HTTP 200 com sinal vermelho. Entradas invalidas e falhas tecnicas usam `ProblemDetail`.

## Fases

1. Scaffolding, H2, Flyway, OpenAPI e dados fake.
2. Regra transacional de bloqueio temporario por aluno no turno e fila com QR Code.
3. Cadastro e reconhecimento facial local no navegador.
4. Consolidacao deterministica e dashboard gerencial.
5. Job persistido, adapter fake e integracao Gemini free tier. **Implementado:** consolidacao, estados do job, porta de IA, adapter fake, adapter Gemini com saida estruturada e fallback para Groq.
6. Historico sintetico, indicadores deterministas no servico Python e separacao entre o que o modelo redige e o que o codigo crava. **Implementado.**
7. Medicao de sobra e composicao das receitas, para aceitacao e desperdicio deixarem de ser NAO_AVALIAVEL. **Implementado:** tabela, endpoint de lancamento, faker e analise por ingrediente.
8. Identidade de item por id e grafo de ingredientes no payload do servico Python. **Implementado:** catalogo de receitas e ingredientes, FK nas tres tabelas de serie, fim do casamento por nome.
9. Testes de concorrencia, privacidade, acessibilidade e documentacao final.

## Criterios de aceite

- A primeira leitura autoriza, uma repeticao dentro de 2 minutos no mesmo turno bloqueia e uma nova leitura apos a janela configurada autoriza novamente.
- Requisicoes concorrentes produzem exatamente um consumo autorizado.
- QR e face usam o mesmo endpoint e a mesma regra.
- Nenhuma indisponibilidade da IA afeta a fila.
- O LLM recebe somente dados agregados.
- O dashboard reproduz os dados persistidos.
- Ambos os projetos compilam e podem ser executados seguindo seus READMEs.
- Nenhum indicador classificado sem base: sem medicao de sobra, sem cobertura total do cardapio ou abaixo do piso de porcoes, aceitacao e desperdicio saem NAO_AVALIAVEL com o motivo.
- O modelo nao decide classificacao nem direcao de ajuste: `nivelAceitacao` e `riscoDesperdicio` vem do calculo, e recomendacao de aumentar ou reduzir quantidade e proibida no prompt.
- O detalhe por turma que a tela le continua completo em `indicadores_json` mesmo quando o payload do prompt manda so o resumo.
- Nenhum vinculo entre tabelas depende de nome: item fora do catalogo e recusado no cadastro com HTTP 400, em vez de gravado e casado por aproximacao depois.
- O servico Python nao le tabela de dominio alguma: tudo o que ele precisa chega no pedido.
