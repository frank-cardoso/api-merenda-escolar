# Plano de Implementacao — Controle de Merenda Escolar

## Arquitetura

O sistema e formado por tres repositorios: uma API Spring Boot, um frontend Angular e um servico Python (FastAPI) de calculos deterministicos e reconhecimento facial. A API e um monolito modular organizado por capacidade de negocio: `aluno`, `cardapio`, `fila`, `relatorio`, `inteligencia` e `shared`.

Existem dois fluxos deliberadamente separados:

1. A fila e sincrona, transacional e nunca acessa o LLM.
2. A gestao consolida dados e cria jobs assincronos para analise pelo LLM.

A integracao Gemini fica na API atras de `AnaliseLogisticaPort`, com adapters `fake` e `gemini`. O modelo configurado por padrao no prototipo e `gemini-3.6-flash`, com override por `GEMINI_MODEL`. Um servico separado so deve ser considerado quando houver necessidade comprovada de deploy, equipe ou escala independentes.

Aos modulos originais somaram-se `historico` (serie sintetica no formato do legado), `medicao` (sobra medida e composicao das receitas), `analytics` (porta para o servico Python) e `biometria` (porta do reconhecimento facial).

O que o LLM recebe nao e o mesmo objeto que a tela le: `IndicadoresParaLLM` projeta a fotografia para o prompt, enquanto `relatorio_ia.indicadores_json` guarda o detalhe completo. E o codigo deterministico que fecha a conclusao numerica, em `ConclusaoDeterministica`; o modelo redige a partir dela e nao vota em `nivelAceitacao` nem `riscoDesperdicio`.

Item e identificado por `receita.id`, nunca por nome — nome e rotulo de exibicao. O servico Python recebe o grafo receita -> ingrediente no proprio pedido e nao e dono de tabela de dominio nenhuma: e isso que o torna a unica peca portavel para outra base sem alteracao.

O porque de cada uma dessas decisoes esta em `DECISOES-TECNICAS.md`.

## Modelo inicial

Persistidos hoje:

- `Aluno`: UUID, codigo publico do QR, matricula, nome e situacao. Nao tem mais campo de turma. Guarda `rosto_embedding_json` (so o vetor facial, nunca a foto; nulo quando nao ha rosto cadastrado).
- `Turma`: entidade propria (codigo, descricao, turno, capacidade).
- `Enturmacao`: vinculo aluno-turma com `dataInicio` e `dataFim` (nula = vigente). Vinculos do mesmo aluno nao se sobrepoem (409).
- `Cardapio`: data, turno, itens e quantidade planejada. Soft delete preserva a auditoria.
- `AuditoriaConsumo`: aluno, cardapio, turno, metodo, resultado, motivo, correlation ID e a turma carimbada (`turmaId`/`turmaNome`) no momento do consumo. Aluno sem enturmacao vigente recebe "Sem turma".
- `RelatorioIA`: periodo, status persistido, entrada agregada, resultado, modelo, erro e a fotografia de indicadores do momento da geracao.
- `Receita`: item que pode ir ao cardapio, com id proprio. `codigo_externo` reservado para o `RECEITAS.ID` do legado.
- `Ingrediente`: id proprio, equivalente a `INGREDIENTES.ID` do legado.
- `ReceitaIngrediente`: composicao, por id nos dois lados. Base da analise de aceitacao por ingrediente.
- `HistoricoConsumo`: um registro por (data, turno, escola, refeicao, receita), no mesmo grao do legado. Sintetico, gerado por `scripts/gerar_historico_fake.py`.
- `MedicaoSobra`: sobra medida por (data, turno, escola, receita), em porcoes. Guarda `sobra_nao_distribuida` e `resto_no_prato` separados — so a segunda mede rejeicao.

Nao existe entidade propria de biometria: o embedding facial e uma coluna de `Aluno`. Consentimento, versao do modelo e prazo de retencao nao foram implementados (ver "Identificacao" e `DECISOES-TECNICAS.md`, secao 8).

DTOs e contratos imutaveis usam records. Entidades JPA permanecem classes. Datas operacionais usam `LocalDate`, instantes usam `Instant` e enums sao persistidos como texto.

## Contratos REST

Implementados:

- `POST /api/v1/fila/validacoes`: autoriza ou bloqueia o consumo. `QR_CODE` exige `alunoCodigo` (sem ele, 400). `FACIAL` exige so `fotoBase64`: o aluno e identificado pelo rosto entre todos os cadastrados (1:N) e `alunoCodigo` e ignorado se vier.
- `GET|POST|PUT|DELETE /api/v1/cardapios`: gestao do cardapio.
- `GET /api/v1/receitas`: catalogo, para o cadastro de cardapio escolher receita por id em vez de digitar nome.
- `POST /api/v1/medicoes-sobra`: lanca a sobra medida de uma receita ao fechar o turno. Recebe `receitaId`. Relancar a mesma receita no mesmo dia e turno sobrescreve.
- `GET /api/v1/gestao/consolidacoes`: consolidacao do dia.
- `GET /api/v1/gestao/indicadores`: indicadores deterministas.
- `POST /api/v1/relatorios-ia`: cria job e retorna o id.
- `GET /api/v1/relatorios-ia/{id}`: consulta status e resultado.
- `GET /api/v1/relatorios-ia?data=&turno=`: relatorios salvos do dia e turno.
- `GET|POST|PUT /api/v1/alunos` e `GET /api/v1/alunos/{id}` (com `turmaVigente` e `historicoVinculos`).
- `GET|POST|PUT|DELETE /api/v1/turmas`.
- `POST /api/v1/alunos/{id}/transferencias` com `{turmaId, dataMudanca}`: fecha o vinculo vigente e abre o novo.
- `POST /api/v1/alunos/{id}/rosto` com `{fotoBase64}`: gera o embedding e guarda so o vetor. Rosto ja cadastrado em outro aluno ativo: 409 ("Este rosto ja esta cadastrado para o aluno <codigo> (<nome>)."); servico facial fora do ar: 503; foto sem rosto ou com mais de um: 400. Recadastrar o rosto do proprio aluno e permitido.

No servico Python (interno, a API e o unico cliente): `POST /api/v1/reconhecimento-facial/embeddings` (exatamente um rosto) e `POST /api/v1/reconhecimento-facial/identificacoes` (1:N, recebe a foto e os embeddings dos candidatos).

Previstos no desenho, ainda nao implementados:

- `GET /api/v1/consumos`: consulta da auditoria.
- `POST /api/v1/relatorios-ia/{id}/reprocessamentos`: reprocessa falhas.

Bloqueios de negocio retornam HTTP 200 com sinal vermelho. Entradas invalidas e falhas tecnicas usam `ProblemDetail`.

## Identificacao

A fila identifica por QR Code ou por reconhecimento facial 1:N. O QR Code e o caminho de reserva: toda falha do facial bloqueia com motivo e orienta o QR.

**Facial.** A foto da fila vai para a API, que carrega os alunos ativos com rosto cadastrado e pede ao servico Python a identificacao (`Facenet`, detector `opencv`, distancia cosseno, limiar 0.4). Vale o maior rosto da foto. A decisao e `RECONHECIDO` so quando a menor distancia fica abaixo do limiar e o segundo colocado esta pelo menos `MARGEM_MINIMA = 0.08` mais longe; senao `AMBIGUO` ou `NAO_RECONHECIDO`. A identificacao roda antes da transacao de validacao; com o aluno identificado, valem as mesmas regras do QR (cardapio, repeticao, auditoria com turma carimbada).

Motivos de bloqueio do facial, nenhum auditado (nao ha aluno a associar): `FOTO_NAO_INFORMADA`, `NENHUM_ROSTO_CADASTRADO`, `ROSTO_NAO_RECONHECIDO`, `ROSTO_AMBIGUO`, `ROSTO_NAO_DETECTADO` e `RECONHECIMENTO_FACIAL_INDISPONIVEL` (Python fora do ar, 5xx ou resposta malformada: nunca 500).

Por que esse desenho, a correcao sobre o `betha-bio-sdk`, a calibracao medida e o que faltaria para producao (LGPD incluida) estao em `DECISOES-TECNICAS.md`, secao 8. Limites assumidos: sem prova de vida (foto de foto passa; a merendeira supervisiona), busca em todos os cadastrados porque nao existe entidade escola, e foto nunca persistida.

**Demonstracao.** `RostosDemonstracaoLoader` cria `ALU-9001` a `ALU-9005` com o embedding ja calculado (`src/main/resources/demo/rostos-demo.json`), gerado por `analytics-merenda-escolar/scripts/gerar_rostos_demo.py` a partir de adultos do LFW. As imagens de teste, diferentes das de cadastro, ficam em `demo/rostos-teste/`.

## Fases

1. Scaffolding, H2, Flyway, OpenAPI e dados fake.
2. Regra transacional de bloqueio temporario por aluno no turno e fila com QR Code.
3. Cadastro e identificacao facial. **Implementado**, em duas etapas: primeiro 1:1 (o operador informava o codigo e a foto confirmava), depois 1:N, em que a foto descobre o aluno. A comparacao roda no servico Python e nao no navegador (o plano original, `face-api.js` local, nao foi seguido). Inclui cadastro de rosto com recusa de duplicado, "Usar imagem" no front e alunos de demonstracao com rosto.
4. Consolidacao deterministica e dashboard gerencial.
5. Job persistido, adapter fake e integracao Gemini free tier. **Implementado:** consolidacao, estados do job, porta de IA, adapter fake, adapter Gemini com saida estruturada e fallback para Groq.
6. Historico sintetico, indicadores deterministas no servico Python e separacao entre o que o modelo redige e o que o codigo crava. **Implementado.**
7. Medicao de sobra e composicao das receitas, para aceitacao e desperdicio deixarem de ser NAO_AVALIAVEL. **Implementado:** tabela, endpoint de lancamento, faker e analise por ingrediente.
8. Identidade de item por id e grafo de ingredientes no payload do servico Python. **Implementado:** catalogo de receitas e ingredientes, FK nas tres tabelas de serie, fim do casamento por nome.
9. Testes de concorrencia, privacidade, acessibilidade e documentacao final.
10. Turma como entidade e enturmacao com vigencia. **Implementado:** migration V10, carimbo de turma na auditoria (autorizado e bloqueado), balde "Sem turma", contrato Java-Python em `schemaVersion=4` com `turmaId` em `porTurma` (relatorios "2" e "3" continuam abrindo), rotas de alunos, turmas e transferencia, feature `cadastro` no front. **Pendente:** adesao por turma segue `NAO_AVALIAVEL`, pois depende de presenca elegivel, e a V10 nao preenche o carimbo das linhas de auditoria anteriores a ela.

## Criterios de aceite

- A primeira leitura autoriza, uma repeticao dentro de 2 minutos no mesmo turno bloqueia e uma nova leitura apos a janela configurada autoriza novamente.
- Requisicoes concorrentes produzem exatamente um consumo autorizado.
- Nenhuma indisponibilidade da IA afeta a fila.
- O LLM recebe somente dados agregados.
- O dashboard reproduz os dados persistidos.
- Ambos os projetos compilam e podem ser executados seguindo seus READMEs.
- Nenhum indicador classificado sem base: sem medicao de sobra, sem cobertura total do cardapio ou abaixo do piso de porcoes, aceitacao e desperdicio saem NAO_AVALIAVEL com o motivo.
- O modelo nao decide classificacao nem direcao de ajuste: `nivelAceitacao` e `riscoDesperdicio` vem do calculo, e recomendacao de aumentar ou reduzir quantidade e proibida no prompt.
- O detalhe por turma que a tela le continua completo em `indicadores_json` mesmo quando o payload do prompt manda so o resumo.
- Nenhum vinculo entre tabelas depende de nome: item fora do catalogo e recusado no cadastro com HTTP 400, em vez de gravado e casado por aproximacao depois.
- O servico Python nao le tabela de dominio alguma: tudo o que ele precisa chega no pedido.
- Consumo e agrupado pela turma vigente na data em que ocorreu (carimbo), nao pela turma atual do aluno; transferir um aluno nao altera o historico.
- Adesao por turma continua `NAO_AVALIAVEL`: turma modelada nao substitui presenca elegivel.
- Identificacao facial 1:N: com `ALU-9001` a `ALU-9005` carregados, a imagem de teste de cada um autoriza o aluno certo na primeira leitura; repetir dentro de 2 minutos bloqueia por consumo recente.
- A imagem `desconhecido.jpg` bloqueia com `ROSTO_NAO_RECONHECIDO`; duvida entre dois cadastros bloqueia com `ROSTO_AMBIGUO`; nunca libera o aluno errado por margem insuficiente.
- Falha do servico facial bloqueia a leitura com `RECONHECIMENTO_FACIAL_INDISPONIVEL` e nao afeta a fila por QR Code.
- Foto nunca e persistida; so o embedding fica em `aluno.rosto_embedding_json`.
- Cadastro de rosto ja registrado em outro aluno ativo e recusado com 409.
