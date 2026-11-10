# Identificação facial 1:N e rostos de demonstração — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** na fila, o modo facial passa a descobrir sozinho quem é o aluno (busca 1:N), e o sistema sobe com alunos de exemplo (rostos LFW) prontos para a apresentação.

**Architecture:** o Python ganha `POST /api/v1/reconhecimento-facial/identificacoes`, que recebe a foto e os candidatos (id + embedding) e devolve a decisão. A API carrega os candidatos, chama esse endpoint **fora** da transação da fila e segue o fluxo de consumo atual com o aluno identificado. O cadastro de rosto usa a mesma busca para recusar duplicado. Uma semente JSON commitada traz 5 alunos de exemplo com embedding pronto. A 1:1 sai por completo.

**Tech Stack:** FastAPI + DeepFace 0.0.103 (Facenet, detector opencv, métrica cosine); Spring Boot 3.5 / Java 25 / JPA / Mockito / MockMvc; Angular 20 (signals, Karma + Jasmine).

**Spec:** `api-merenda-escolar/docs/superpowers/specs/2026-11-07-identificacao-facial-design.md`

Caminhos relativos a `/home/frank.serrano/Documentos/projeto ia/`. Pacote Java: `br.com.frankcardoso.merenda`, que nos caminhos aparece como `.../merenda/`, com raiz em `api-merenda-escolar/src/main/java/br/com/frankcardoso`. Testes Java ficam no espelho em `src/test/java`.

## Global Constraints

- Sem `git add`/`commit`/`push`. Ao fim de cada task, mostrar `git diff --stat`. O Frank decide quando commitar e é perguntado sobre ticket a cada commit. Este plano e a spec **não** vão para o git.
- Mesmo modelo nas duas pontas: `Facenet`, `detector_backend="opencv"`, métrica `cosine`. Limiar = `deepface.modules.verification.find_threshold("Facenet", "cosine")`, que retorna `0.4`.
- Foto nunca é persistida, nem no Python, nem na API, nem no front. Só o embedding fica, em `aluno.rosto_embedding_json`.
- A fila nunca chama LLM. Falha de reconhecimento bloqueia com motivo e orienta QR Code, nunca devolve 500.
- Teste automatizado não carrega foto nem embedding de pessoa real: use vetores inventados (`[1.0, 0.0]` etc.). As imagens LFW só existem como material de demonstração (Task 9).
- Códigos dos alunos de exemplo: `ALU-9001` a `ALU-9005`, matrículas `20269001` a `20269005`.
- Comentários: só o que o código não diz; sem número de ticket. Textos de tela em pt-BR com acento.
- Comandos:
  - **Python:** `cd analytics-merenda-escolar && ./.venv/bin/python -m pytest -q`. Ruff vale só nos arquivos tocados; há 5 erros antigos em `indicadores_logisticos.py` que não se mexe.
  - **Java:** definir `MVN="JAVA_HOME=$HOME/.jdks/corretto-25.0.4.1 $HOME/.m2/wrapper/dists/apache-maven-3.9.16-bin/5grr65jo27hi51sujmtcldfovl/apache-maven-3.9.16/bin/mvn"`. O `mvn` do PATH é 3.5.4/Java 8 e não serve. Rodar `eval $MVN -B -q test` em `api-merenda-escolar`. **A API pode estar rodando via `spring-boot:run` no mesmo `target/`**: parar antes de compilar ou testar. Com `ss -ltnp | grep 8081` ache o PID e encerre o grupo de processos.
  - **Front:** `cd front-merenda-escolar && PATH=$HOME/.nvm/versions/node/v20.19.0/bin:$PATH CHROME_BIN=$HOME/.cache/ms-playwright/chromium-1187/chrome-linux/chrome pnpm test --watch=false --browsers=ChromeHeadless`.

## Review Focus

- **Serviço Python fora do ar durante a fila facial:** a leitura sai BLOQUEADO com `RECONHECIMENTO_FACIAL_INDISPONIVEL` e a tela orienta o QR, sem 500. Nesse caso o cadastro de rosto devolve 503 com mensagem. Testes nas Tasks 2, 4 e 5.
- **Foto da fila com mais de um rosto** (criança de trás aparecendo): vale o maior rosto. Teste na Task 1.
- **Arquivo que não é imagem no "Usar imagem"** (PDF, txt): mensagem na tela, nada é emitido nem enviado. Teste na Task 7.
- **Corpo FACIAL com `alunoCodigo` de outro aluno:** o código é ignorado e vale o rosto. QR sem `alunoCodigo` devolve 400. Testes na Task 4.
- **Candidato com embedding de dimensão diferente** (cadastrado com outro modelo): é ignorado, os demais seguem comparados e a resposta conta os ignorados. A API registra aviso no log. Testes nas Tasks 1 e 3.

---

### Task 1: Python — identificação 1:N e remoção da 1:1

**Files:**
- Modify: `analytics-merenda-escolar/app/domain/reconhecimento_facial.py`. Reescrever a docstring do módulo, que hoje diz "Não é busca entre todos (1:N)": a busca passa a ser 1:N, e o serviço continua sem guardar foto ou embedding.
- Modify: `analytics-merenda-escolar/app/api/reconhecimento_facial_controller.py`
- Test: `analytics-merenda-escolar/tests/domain/test_reconhecimento_facial.py`, `analytics-merenda-escolar/tests/api/test_reconhecimento_facial_controller.py`

**Interfaces:**
- Produces:
  - `class CandidatoRosto(BaseModel)`: `id: str`, `embedding: list[float]` com `min_length=1`.
  - `class IdentificarRostoRequest(BaseModel)`: `foto_base64` (alias `fotoBase64`), `candidatos: list[CandidatoRosto]` com `min_length=1`, `modelo: ModeloReconhecimento = MODELO_PADRAO`.
  - `class IdentificarRostoResponse(BaseModel)`, todos com alias camelCase:
    - `decisao: Literal["RECONHECIDO", "NAO_RECONHECIDO", "AMBIGUO"]`
    - `melhor_candidato_id: str | None`
    - `distancia: float | None`
    - `distancia_segundo: float | None`
    - `limiar: float`
    - `margem_minima: float`
    - `candidatos_ignorados: int`
    - `modelo: str`
  - `MARGEM_MINIMA: float = 0.08`. Valor inicial, recalibrado na Task 9.
  - `def identificar_rosto(request: IdentificarRostoRequest) -> IdentificarRostoResponse`
  - `POST /api/v1/reconhecimento-facial/identificacoes` com `response_model_by_alias=True`. `RostoNaoDetectadoError` vira 422, igual aos endpoints atuais.
  - `gerar_embedding` e `POST /embeddings` ficam como estão.
  - Saem: `verificar_rosto`, `VerificarRostoRequest/Response`, `POST /verificacoes` e os testes deles.

Algoritmo de `identificar_rosto`, que a assinatura não determina:
1. `DeepFace.represent(img_path=foto, model_name=modelo, detector_backend="opencv", enforce_detection=True)`. `ValueError` vira `RostoNaoDetectadoError`.
2. Com vários rostos, usar o de maior `facial_area["w"] * facial_area["h"]`.
3. Candidato cujo `len(embedding)` difere do da foto é ignorado e contado em `candidatos_ignorados`.
4. Para os compatíveis, calcular `float(verification.find_distance(foto, candidato, "cosine"))` e ordenar crescente. Sem compatível nenhum, a decisão é `NAO_RECONHECIDO` com id e distâncias `None`.
5. Se `melhor >= limiar`, `NAO_RECONHECIDO`.
6. Senão, se existe segundo e `segundo - melhor < MARGEM_MINIMA`, `AMBIGUO`.
7. Senão, `RECONHECIDO`.
8. `melhor_candidato_id` é sempre o mais próximo. Distâncias arredondadas com `round(x, 4)`.

- [ ] **Step 1: Escrever os testes que falham.** Use `@patch("app.domain.reconhecimento_facial.DeepFace.represent")`, devolvendo `[{"embedding": [...], "facial_area": {"x": 0, "y": 0, "w": W, "h": H}}]`:
  - `test_identifica_o_candidato_mais_proximo`: foto `[1.0, 0.0]`, candidatos A `[1.0, 0.0]` e B `[0.0, 1.0]`. Espera `decisao == "RECONHECIDO"`, `melhor_candidato_id == "A"`, `distancia == 0.0`, `distancia_segundo == 1.0`, `limiar == 0.4`. O `represent` é chamado com `model_name="Facenet", detector_backend="opencv", enforce_detection=True`.
  - `test_nao_reconhece_quando_todos_acima_do_limiar`: foto `[1.0, 0.0]`, só B `[0.0, 1.0]`. Espera `NAO_RECONHECIDO`, `melhor_candidato_id == "B"`.
  - `test_ambiguo_quando_segundo_esta_perto_demais`: foto `[1.0, 0.0]`, A `[1.0, 0.0]` e B `[1.0, 0.01]`. Espera `AMBIGUO`, `melhor_candidato_id == "A"`.
  - `test_candidato_unico_abaixo_do_limiar_reconhece`: só A `[0.8, 0.6]`. Espera `RECONHECIDO`, `distancia == 0.2` e `distancia_segundo is None`.
  - `test_usa_o_maior_rosto_da_foto`: dois rostos, `[0.0, 1.0]` com área 50×50 e `[1.0, 0.0]` com 120×120. Candidatos A `[1.0, 0.0]` e B `[0.0, 1.0]`. Espera `RECONHECIDO` A.
  - `test_ignora_candidato_de_dimensao_diferente`: A `[1.0, 0.0]` e C `[1.0, 0.0, 0.0]`. Espera `RECONHECIDO` A e `candidatos_ignorados == 1`.
  - `test_todos_incompativeis_nao_reconhece`: só C `[1.0, 0.0, 0.0]`. Espera `NAO_RECONHECIDO`, `melhor_candidato_id is None`, `candidatos_ignorados == 1`.
  - `test_identificacao_sem_rosto_detectado_levanta_erro`: `represent.side_effect = ValueError("Face could not be detected.")`. Espera `pytest.raises(RostoNaoDetectadoError)`.
  - **Controller:**
    - `test_identificacao_facial_responde_em_camel_case`: 200; o JSON tem as chaves `decisao`, `melhorCandidatoId`, `distancia`, `distanciaSegundo`, `limiar`, `margemMinima`, `candidatosIgnorados`, `modelo`.
    - `test_identificacao_sem_rosto_retorna_422`.
    - `test_identificacao_sem_candidatos_retorna_422`, com `candidatos: []`.
    - `test_endpoint_de_verificacao_1a1_nao_existe_mais`: `POST /verificacoes` devolve 404.
- [ ] **Step 2:** rodar `./.venv/bin/python -m pytest -q tests/domain/test_reconhecimento_facial.py tests/api/test_reconhecimento_facial_controller.py`. Esperado: FAIL, porque `identificar_rosto` não existe.
- [ ] **Step 3:** implementar o algoritmo acima no domínio e o endpoint no controller. Remover a 1:1 e os testes dela.
- [ ] **Step 4:** rodar a suíte inteira, `./.venv/bin/python -m pytest -q` (todos passam), e depois `./.venv/bin/python -m ruff check app/domain/reconhecimento_facial.py app/api/reconhecimento_facial_controller.py` (limpo).

### Task 2: API — port e adapter de identificação, sem 1:1

**Files:**
- Modify: `.../merenda/biometria/application/port/ReconhecimentoFacialPort.java`. Reescrever o Javadoc, que hoje diz "nunca busca entre todos".
- Modify: `.../merenda/biometria/infrastructure/python/PythonReconhecimentoFacialAdapter.java`
- Create: `.../merenda/biometria/domain/ReconhecimentoFacialIndisponivelException.java`, que estende `RuntimeException`.
- Test: `src/test/java/.../biometria/infrastructure/python/PythonReconhecimentoFacialAdapterTest.java`

**Interfaces:**
- Consumes: o endpoint da Task 1.
- Produces, no port:
  ```java
  List<Float> gerarEmbedding(String fotoBase64);
  ResultadoIdentificacao identificar(String fotoBase64, List<Candidato> candidatos);
  record Candidato(UUID alunoId, List<Float> embedding) {}
  record ResultadoIdentificacao(Decisao decisao, UUID melhorCandidatoId, Double distancia,
                                Double distanciaSegundo, double limiar, int candidatosIgnorados) {}
  enum Decisao { RECONHECIDO, NAO_RECONHECIDO, AMBIGUO }
  ```
- Saem `verificar` e `ResultadoVerificacaoFacial`.
- O adapter envia `{fotoBase64, candidatos: [{id, embedding}], modelo: "Facenet"}`, com `id` = `alunoId.toString()`.
- `ResourceAccessException`, nos dois métodos, vira `ReconhecimentoFacialIndisponivelException`. O 422 continua traduzido como hoje (`traduzirErro`).

- [ ] **Step 1: testes que falham**, com `MockRestServiceServer`, no padrão do teste atual:
  - `identificarEnviaFotoECandidatosEMapeiaResposta`: confere o corpo JSON enviado a `/api/v1/reconhecimento-facial/identificacoes` e o mapeamento de `decisao`, `melhorCandidatoId` e `candidatosIgnorados`.
  - `identificarComRespostaSemCandidatoMapeiaNulos`: `melhorCandidatoId` e `distancia` vêm `null`.
  - `identificarSemRostoLevantaRostoNaoDetectado`: o Python responde 422.
  - `servicoForaDoArLevantaIndisponivel`: o servidor mock lança `ResourceAccessException`, via `withException(new java.net.ConnectException())`.
  - Remover os testes de `verificar`.
- [ ] **Step 2:** parar a API se estiver no ar e rodar `eval $MVN -B -q test -Dtest=PythonReconhecimentoFacialAdapterTest`. Esperado: FAIL de compilação.
- [ ] **Step 3:** implementar port, adapter e exceção. Os usos antigos de `verificar` em `ValidacaoConsumoService` quebram e se resolvem na Task 4. Para compilar já agora, o `conferirRosto` antigo pode ficar provisoriamente lançando `UnsupportedOperationException`; ele sai na Task 4.
- [ ] **Step 4:** rodar o mesmo comando. Esperado: PASS.

### Task 3: API — serviço de identificação facial

**Files:**
- Create: `.../merenda/biometria/application/IdentificacaoFacialService.java` (`@Service`)
- Create: `.../merenda/biometria/application/IdentificacaoFacial.java`
- Modify: `.../merenda/aluno/infrastructure/AlunoRepository.java`
- Test: `src/test/java/.../biometria/application/IdentificacaoFacialServiceTest.java`, com Mockito, `ReconhecimentoFacialPort` e `AlunoRepository` mockados.

**Interfaces:**
- Consumes: o port da Task 2.
- Produces:
  ```java
  public record IdentificacaoFacial(Resultado resultado, Aluno aluno) {
      public enum Resultado { RECONHECIDO, NAO_RECONHECIDO, AMBIGUO, SEM_CANDIDATOS }
  }
  public IdentificacaoFacial identificar(String fotoBase64);                       // fila
  public Optional<Aluno> alunoComMesmoRosto(String fotoBase64, UUID alunoIgnorado); // cadastro
  ```
- `aluno` é o mais próximo quando o resultado é `RECONHECIDO` ou `AMBIGUO`, e `null` nos outros casos.
- `alunoComMesmoRosto` retorna o mais próximo quando a decisão é `RECONHECIDO` ou `AMBIGUO` (distância abaixo do limiar) e vazio quando é `NAO_RECONHECIDO` ou não há candidato, excluindo `alunoIgnorado`.
- Exceções do port (`RostoNaoDetectadoException`, `ReconhecimentoFacialIndisponivelException`) sobem sem tratamento.
- O serviço lê os candidatos com `List<Aluno> findByAtivoTrueAndRostoEmbeddingJsonIsNotNull()` e converte o JSON com `ObjectMapper`. O `lerEmbedding` que hoje está em `ValidacaoConsumoService` vem para cá.
- Com `candidatosIgnorados > 0`, registra `LOG.warn`.
- Novos no repositório:
  - `@Lock(PESSIMISTIC_WRITE) Optional<Aluno> findByIdAndAtivoTrue(UUID id)`, usado na Task 4.
  - `Optional<Aluno> findByCodigoPublico(String codigoPublico)`, usado na Task 6.

- [ ] **Step 1: testes que falham:**
  - `semAlunoComRostoDevolveSemCandidatosSemChamarOPython`: `verifyNoInteractions(port)`.
  - `reconhecidoDevolveOAluno`
  - `ambiguoDevolveResultadoAmbiguo`
  - `naoReconhecidoDevolveSemAluno`
  - `enviaSoOsAlunosComRostoComOEmbeddingConvertido`: usa `ArgumentCaptor` sobre os candidatos.
  - `alunoComMesmoRostoIgnoraOProprioAluno`: o captor não contém `alunoIgnorado`.
  - `alunoComMesmoRostoComUnicoCandidatoSendoOProprioDevolveVazio`
  - `alunoComMesmoRostoAbaixoDoLimiarDevolveOOutroAluno`: decisão `AMBIGUO` também devolve o mais próximo.
- [ ] **Step 2:** `eval $MVN -B -q test -Dtest=IdentificacaoFacialServiceTest`. Esperado: FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** rodar o mesmo comando. Esperado: PASS.

### Task 4: API — fila facial 1:N fora da transação

**Files:**
- Modify: `.../merenda/fila/api/ValidarConsumoRequest.java`
- Modify: `.../merenda/fila/application/ValidacaoConsumoService.java`
- Test: `src/test/java/.../fila/application/ValidacaoConsumoServiceTest.java`
- Test: `src/test/java/.../fila/api/ValidarConsumoRequestTest.java`, novo, com `jakarta.validation.Validation.buildDefaultValidatorFactory()`.

**Interfaces:**
- Consumes: `IdentificacaoFacialService.identificar` (Task 3) e `AlunoRepository.findByIdAndAtivoTrue` (Task 3).
- **Request:** `alunoCodigo` perde o `@NotBlank`. Entra `@AssertTrue(message = "alunoCodigo: obrigatorio para QR_CODE") @JsonIgnore public boolean isAlunoCodigoInformadoParaQrCode()`. O Javadoc é reescrito: em FACIAL vale só a foto e `alunoCodigo` é ignorado.
- **Serviço:** passa a depender de `IdentificacaoFacialService` em vez do port. No FACIAL, a identificação acontece em `validar`, **antes** do `transactionTemplate.execute`. Dentro da transação o aluno vem de `findByIdAndAtivoTrue(id)` no FACIAL e de `findByCodigoPublicoAndAtivoTrue(codigo)` no QR. A auditoria grava o método do request.
- **Mapeamento de bloqueio.** Nenhum desses casos grava auditoria:

  | Situação | Motivo |
  |---|---|
  | foto nula ou em branco | `FOTO_NAO_INFORMADA` |
  | `SEM_CANDIDATOS` | `NENHUM_ROSTO_CADASTRADO` |
  | `NAO_RECONHECIDO` | `ROSTO_NAO_RECONHECIDO` |
  | `AMBIGUO` | `ROSTO_AMBIGUO` |
  | `RostoNaoDetectadoException` | `ROSTO_NAO_DETECTADO` |
  | `ReconhecimentoFacialIndisponivelException` | `RECONHECIMENTO_FACIAL_INDISPONIVEL` |

- Saem `conferirRosto`, `lerEmbedding` e os motivos `ROSTO_NAO_CONFERE` e `ALUNO_SEM_ROSTO_CADASTRADO`.

- [ ] **Step 1: testes que falham.** No `ValidacaoConsumoServiceTest`, troque o mock do port por `@Mock IdentificacaoFacialService` e remova os testes facial 1:1.
  - `facialReconhecidoAutorizaOAlunoIdentificado`: o `alunoNome` da resposta é o do aluno identificado e a auditoria tem `MetodoIdentificacao.FACIAL`.
  - `facialIgnoraAlunoCodigoDoCorpo`: o request traz `alunoCodigo` "ALU-999" e o reconhecido é outro aluno. Autoriza o reconhecido e `verify(alunoRepository, never()).findByCodigoPublicoAndAtivoTrue(any())`.
  - `facialIdentificaAntesDaTransacao`: `InOrder` mostra `identificacaoFacial.identificar` antes de `transactionTemplate.execute`.
  - Um teste por linha da tabela de mapeamento, cada um com `verify(auditoriaRepository, never()).save(any())`:
    - `facialSemFotoBloqueiaComFotoNaoInformada`
    - `facialSemCandidatosBloqueiaComNenhumRostoCadastrado`
    - `facialNaoReconhecidoBloqueia`
    - `facialAmbiguoBloqueia`
    - `facialSemRostoNaFotoBloqueia`
    - `facialComServicoForaDoArBloqueiaComIndisponivel`
  - `facialReconhecidoSegueBloqueioDeRepeticao`: com consumo recente, sai `CONSUMO_RECENTE_BLOQUEADO`.
  - No `ValidarConsumoRequestTest`:
    - `qrSemCodigoEhInvalido`
    - `qrComCodigoEhValido`
    - `facialSemCodigoEhValido`
- [ ] **Step 2:** `eval $MVN -B -q test -Dtest='ValidacaoConsumoServiceTest,ValidarConsumoRequestTest'`. Esperado: FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** rodar a suíte inteira com `eval $MVN -B -q test`. Esperado: PASS, inclusive os testes de concorrência da fila que já existem.

### Task 5: API — cadastro recusa rosto já cadastrado em outro aluno

**Files:**
- Modify: `.../merenda/aluno/application/AlunoService.java`
- Modify: `.../merenda/shared/web/RestExceptionHandler.java`
- Test: `src/test/java/.../turma/application/CadastroHttpTest.java`. Ele já usa `@MockitoBean ReconhecimentoFacialPort`, e o duplicado é configurado pelo mock de `identificar`.

**Interfaces:**
- Consumes: `IdentificacaoFacialService.alunoComMesmoRosto` (Task 3).
- `cadastrarRosto(id, foto)` chama `alunoComMesmoRosto(foto, id)` **antes** de `gerarEmbedding`.
  - Se encontrar alguém, lança `ConflitoCadastroException("Este rosto já está cadastrado para o aluno " + codigoPublico + " (" + nome + ").")`, que vira 409 pelo handler existente, e nada é gravado.
- No `RestExceptionHandler`, `ReconhecimentoFacialIndisponivelException` vira 503 com título "Servico indisponivel" e a mensagem "Reconhecimento facial indisponível no momento. Tente novamente em instantes.".

- [ ] **Step 1: testes que falham:**
  - `cadastrarRostoJaCadastradoEmOutroAlunoDevolve409`: o mock de `identificar` devolve `RECONHECIDO` com o id de outro aluno, que tem rosto. Espera status 409, `detail` contendo o código do outro aluno e `temRostoCadastrado` do aluno alvo ainda `false`.
  - `recadastrarOProprioRostoContinuaPermitido`: o único aluno com rosto é o próprio. Espera 200.
  - `cadastrarRostoComServicoForaDoArDevolve503`.
  - Manter `cadastrarRostoGuardaSoOEmbeddingENaoAFoto` e `cadastrarRostoSemRostoDetectavelDevolve400`, ajustando os mocks para `identificar`.
- [ ] **Step 2:** `eval $MVN -B -q test -Dtest=CadastroHttpTest`. Esperado: FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** rodar o mesmo comando (PASS) e depois a suíte inteira (PASS).

### Task 6: API — semente de rostos e demonstração que não trava

**Files:**
- Create: `.../merenda/shared/config/RostosDemonstracaoLoader.java`, com `@Component @Profile("!test") @Order(5)`.
- Modify: `.../merenda/shared/config/DadosDemonstracaoLoader.java`, que passa de `@Order(5)` para `@Order(6)`.
- Create: `api-merenda-escolar/src/test/resources/demo/rostos-demo-teste.json`, com 2 alunos fictícios e embeddings inventados de 3 dimensões.
- Test: `src/test/java/.../shared/config/RostosDemonstracaoLoaderTest.java`, novo, no estilo do `DadosDemonstracaoTurmasTest` (`@SpringBootTest`, instanciando o loader à mão).
- Test: `src/test/java/.../shared/config/DadosDemonstracaoTurmasTest.java`

**Interfaces:**
- Consumes: `AlunoRepository.findByCodigoPublico` (Task 3) e `Aluno.cadastrarRosto(String)`, que já existe.
- Produces:
  - O formato da semente, que a Task 9 grava:
    ```json
    {"origem": "...", "modelo": "Facenet",
     "alunos": [{"codigoPublico": "ALU-9001", "matricula": "20269001", "nome": "Helena Duarte",
                 "lfwIdentidade": "...", "imagemTeste": "ALU-9001-teste.jpg", "embedding": [0.1, ...]}]}
    ```
  - Construtor: `RostosDemonstracaoLoader(AlunoRepository alunos, ObjectMapper objectMapper, Clock clock, @Value("${merenda.demo.rostos:classpath:demo/rostos-demo.json}") Resource semente)`.
  - Método: `@EventListener(ApplicationReadyEvent.class) @Transactional public void carregar()`.
- Regras de carga:
  - Recurso ausente: `LOG.info` e retorna.
  - Aluno inexistente pelo código: é criado ativo com o rosto.
  - Aluno existente sem rosto: recebe o rosto.
  - Aluno existente com rosto: fica intocado.
  - Matrícula já usada por outro código: `LOG.warn` e pula.
- Mudanças no `DadosDemonstracaoLoader`:
  1. Alunos com `temRostoCadastrado()` ficam fora de `gerarConsumos`.
  2. Nenhuma `AuditoriaConsumo` com `instante` depois de `Instant.now(clock)` é gravada.
  3. O cardápio de hoje é garantido em **todos** os valores de `Turno`, inclusive em sábado e domingo. Hoje só NOITE é garantida fora de dia útil.

- [ ] **Step 1: testes que falham.**
  - No `RostosDemonstracaoLoaderTest`:
    - `criaOsAlunosDaSementeComRosto`
    - `segundaCargaNaoDuplicaNemSobrescreveEmbedding`: o JSON salvo não muda.
    - `naoSobrescreveRostoDeAlunoExistente`
    - `preencheRostoDeAlunoExistenteSemRosto`
    - `recursoAusenteNaoFalha`, com `new ClassPathResource("demo/nao-existe.json")`.
  - No `DadosDemonstracaoTurmasTest`, onde o relógio está fixo em `HOJE` às 10:00:
    - `nenhumConsumoGeradoDepoisDoInstanteAtual`: todo `instante <= relogio.instant()`.
    - `alunoComRostoFicaForaDoConsumoSintetico`: no `@BeforeEach`, antes do `carregar()`, salvar o aluno `ALU-9001` com `cadastrarRosto("[1.0,0.0]")`. Espera zero auditorias dele e uma enturmação vigente, que vem do `garantirEnturmacoes` existente.
    - `cardapioDeHojeExisteEmTodosOsTurnos`.
  - Os testes existentes que contam consumos de hoje podem mudar, porque o turno da tarde deixa de ser gerado às 10h. Ajuste as expectativas e registre no resumo da task.
- [ ] **Step 2:** `eval $MVN -B -q test -Dtest='RostosDemonstracaoLoaderTest,DadosDemonstracaoTurmasTest'`. Esperado: FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** rodar o mesmo comando (PASS) e a suíte inteira (PASS).

### Task 7: Front — "Usar imagem" no componente de câmera

**Files:**
- Modify: `front-merenda-escolar/src/app/shared/components/camera-foto/camera-foto.component.ts`. Tirar o `ngAfterViewInit` vazio e o `AfterViewInit`. O comentário sobre "só inicia sob clique" vai para `iniciarCamera`, se ainda fizer sentido.
- Modify: `front-merenda-escolar/src/app/shared/components/camera-foto/camera-foto.component.html`
- Test: `front-merenda-escolar/src/app/shared/components/camera-foto/camera-foto.component.spec.ts`, novo.

**Interfaces:**
- Produces:
  - `async carregarImagem(evento: Event): Promise<void>`. Lê o primeiro arquivo de `(evento.target as HTMLInputElement).files` com `FileReader.readAsDataURL`, embrulhado em Promise.
  - Se `!arquivo.type.startsWith('image/')`, a mensagem vira `'Escolha um arquivo de imagem (JPG ou PNG).'` e nada é emitido.
  - Se for imagem, faz `ultimaFoto.set(dataUrl)`, `mensagem.set('Imagem carregada. Pode enviar ou escolher outra.')` e `fotoCapturada.emit(dataUrl)`.
  - O `output` `fotoCapturada` não muda, então fila e cadastro funcionam sem alteração.
- Template: um botão "Usar imagem", visível nos dois estados da câmera e ao lado de "Iniciar câmera" ou "Capturar foto", que aciona um `<input type="file" accept="image/*" class="hidden">`. O input é limpo após cada leitura (`value = ''`), para aceitar o mesmo arquivo de novo.

- [ ] **Step 1: testes que falham:**
  - `emite o data URL da imagem escolhida`: `new File(['abc'], 'x.png', { type: 'image/png' })`. Espera emitir `'data:image/png;base64,YWJj'`.
  - `recusa arquivo que nao e imagem`: `type: 'application/pdf'`. Nada é emitido e a mensagem é a de recusa.
  - `nao pede permissao da camera ao abrir`: `spyOn(navigator.mediaDevices, 'getUserMedia')` não é chamado após `fixture.detectChanges()`.
- [ ] **Step 2:** rodar o comando de testes do front. Esperado: FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** rodar o mesmo comando. Esperado: PASS, todas as specs.

### Task 8: Front — fila 1:N e motivos novos

**Files:**
- Modify: `front-merenda-escolar/src/app/features/fila/models/fila.models.ts`. `alunoCodigo` passa a ser opcional e o comentário de `fotoBase64` é reescrito.
- Modify: `front-merenda-escolar/src/app/features/fila/pages/fila-page/fila-page.component.ts`. Sai o signal `codigoAlunoFacial`; o comentário "facial exige, porque é verificação 1:1" é reescrito.
- Modify: `front-merenda-escolar/src/app/features/fila/pages/fila-page/fila-page.component.html`. Saem o campo "Código do aluno" e o texto "Confirma 1:1...". Entra a frase: "O sistema procura o rosto entre os alunos cadastrados. Se não reconhecer, use o QR Code."
- Modify: `front-merenda-escolar/src/app/features/fila/components/resultado-validacao/resultado-validacao.component.ts`
- Test: `.../resultado-validacao/resultado-validacao.component.spec.ts` e `.../fila-page/fila-page.component.spec.ts`, novo, com `provideHttpClient()` e `provideHttpClientTesting()`.

**Interfaces:**
- `validarFacial(fotoBase64)` envia `{ metodoIdentificacao: 'FACIAL', fotoBase64 }`, sem a chave `alunoCodigo`.
- Em `MOTIVOS`, saem `ROSTO_NAO_CONFERE` e `ALUNO_SEM_ROSTO_CADASTRADO` e entram:
  - `ROSTO_NAO_RECONHECIDO: 'Rosto não reconhecido entre os alunos cadastrados. Use o QR Code.'`
  - `ROSTO_AMBIGUO: 'Rosto parecido com mais de um aluno. Use o QR Code para confirmar.'`
  - `NENHUM_ROSTO_CADASTRADO: 'Nenhum aluno tem rosto cadastrado ainda. Use o QR Code.'`
  - `RECONHECIMENTO_FACIAL_INDISPONIVEL: 'Reconhecimento facial indisponível no momento. Use o QR Code.'`

- [ ] **Step 1: testes que falham:**
  - `fila-page`, `envia a foto sem codigo de aluno no modo facial`: com `HttpTestingController.expectOne` na URL que o `FilaApiService` usa. Espera `req.request.body` igual a `{ metodoIdentificacao: 'FACIAL', fotoBase64: 'data:image/jpeg;base64,AAA' }`, e `'alunoCodigo' in body` é `false`.
  - `fila-page`, `mostra quem foi reconhecido`: responde AUTORIZADO com `alunoNome: 'Helena Duarte'` e o texto aparece na página.
  - `resultado-validacao`: um caso por motivo novo, conferindo o texto exato acima.
- [ ] **Step 2:** rodar o comando de testes do front. Esperado: FAIL.
- [ ] **Step 3:** implementar.
- [ ] **Step 4:** rodar o mesmo comando. Esperado: PASS.

### Task 9: Rostos LFW, semente real e calibração

**Files:**
- Create: `analytics-merenda-escolar/scripts/gerar_rostos_demo.py`
- Create: `api-merenda-escolar/src/main/resources/demo/rostos-demo.json`, gerado pelo script.
- Create: `api-merenda-escolar/demo/rostos-teste/` com `ALU-9001-teste.jpg` … `ALU-9005-teste.jpg`, `desconhecido.jpg` e `README.md`. Tudo gerado pelo script.

**Interfaces:**
- Consumes: `gerar_embedding`, `identificar_rosto` e os modelos da Task 1, importados de `app.domain.reconhecimento_facial`, e o formato da semente da Task 6.
- CLI: `python scripts/gerar_rostos_demo.py --lfw-dir PATH [--semente PATH] [--imagens PATH] [--seed 2026]`.
  - O padrão de `--semente` é `../api-merenda-escolar/src/main/resources/demo/rostos-demo.json`.
  - O padrão de `--imagens` é `../api-merenda-escolar/demo/rostos-teste`.
  - As imagens são passadas como data URL (`data:image/jpeg;base64,...`), o mesmo formato que o front envia.
- Alunos fixos, em ordem:

  | Código | Nome |
  |---|---|
  | ALU-9001 | Helena Duarte |
  | ALU-9002 | Rafael Moreira |
  | ALU-9003 | Lívia Campos |
  | ALU-9004 | Otávio Brandão |
  | ALU-9005 | Marina Teles |

- Seleção:
  1. Pegar as identidades LFW com 2 a 4 imagens (menos conhecidas), embaralhadas com `random.Random(seed)`.
  2. A imagem de cadastro é a `_0001` e precisa passar em `gerar_embedding` com exatamente 1 rosto. A imagem de teste é a `_0002`.
  3. Aceitar uma identidade nova só se, com o conjunto aceito até ali, **toda** imagem de teste der `RECONHECIDO` para o próprio aluno. Senão, descartar a identidade nova.
  4. Parar ao chegar em 5.
  5. O desconhecido é a próxima identidade cuja `_0001` dá `NAO_RECONHECIDO` contra os 5.
- Saída no terminal: uma tabela por aluno com a distância de teste para o próprio cadastro, a menor distância para outro aluno e a margem. No fim, a menor margem observada.

- [ ] **Step 1: baixar o LFW** para uma pasta de dados isolada:
  ```bash
  D=/tmp/claude-1975829934/-home-frank-serrano/dd761c4d-f72d-487e-b365-b552c902be76/scratchpad/lfw
  mkdir -p "$D"
  curl -fL -o "$D/lfw.tgz" http://vis-www.cs.umass.edu/lfw/lfw.tgz || curl -fL -o "$D/lfw.tgz" https://ndownloader.figshare.com/files/5976018
  tar -xzf "$D/lfw.tgz" -C "$D"
  ```
  Esperado: `"$D/lfw/<Nome>/<Nome>_0001.jpg"`. Anotar de qual URL veio. Se as duas falharem, parar e avisar o Frank.
- [ ] **Step 2:** escrever o script.
- [ ] **Step 3:** rodar a partir do repo analytics: `./.venv/bin/python scripts/gerar_rostos_demo.py --lfw-dir "$D/lfw"`. Esperado: 5 aceitos, 1 desconhecido, a semente e as imagens gravadas e a tabela de calibração impressa.
- [ ] **Step 4: calibrar.**
  - Se a menor margem entre a pessoa certa e o impostor mais próximo for bem maior que `MARGEM_MINIMA` (0.08), manter o valor.
  - Se o conjunto só passou porque o script descartou muita identidade por `AMBIGUO`, avaliar ajustar `MARGEM_MINIMA` na Task 1. Rodar de novo a suíte Python e o script.
  - Registrar os números (distâncias pessoa certa × impostor, margem escolhida) para a Task 10.
- [ ] **Step 5:** escrever o `README.md` de `demo/rostos-teste/`. Ele diz:
  - qual imagem é de qual aluno de exemplo e que `desconhecido.jpg` não tem cadastro;
  - de onde veio o download;
  - a citação: Gary B. Huang, Manu Ramesh, Tamara Berg, Erik Learned-Miller. "Labeled Faces in the Wild: A Database for Studying Face Recognition in Unconstrained Environments". University of Massachusetts, Amherst, Technical Report 07-49, 2007;
  - que os nomes dos alunos são fictícios.
- [ ] **Step 6:** `./.venv/bin/python -m ruff check scripts/gerar_rostos_demo.py`. Esperado: limpo.

### Task 10: Documentação

**Files:**
- Modify: `api-merenda-escolar/DECISOES-TECNICAS.md` §8
- Modify: `api-merenda-escolar/implementation_plan.md`
- Modify: `api-merenda-escolar/README.md`, `analytics-merenda-escolar/README.md`, `front-merenda-escolar/README.md`

- [ ] **Step 1: reescrever o §8** com o conteúdo da seção "Documentação" da spec:
  - a correção de fato sobre `betha-bio-sdk`: mesma cadeia, worker corrigido em 2026-08-25 e nenhuma aposentadoria confirmada;
  - a decisão de usar tablet ou notebook com supervisão na merenda, e terminal só onde não há supervisão;
  - o que faltaria para produção, LGPD incluída;
  - os números de calibração da Task 9.
- [ ] **Step 2: atualizar o `implementation_plan.md`:**
  - seção "Identificação";
  - fase 3, que deixa de ser "Descartada";
  - modelo: `Aluno` com `rosto_embedding_json`; remover "`BiometriaFacial`… não será";
  - contratos REST: fila FACIAL sem `alunoCodigo` e `POST /alunos/{id}/rosto` com 409 para duplicado;
  - critérios de aceite da identificação.
- [ ] **Step 3: atualizar os READMEs.** Onde hoje dizem 1:1, passa a 1:N.
  - **Analytics:** `/identificacoes` substitui `/verificacoes`.
  - **API:** endpoints e limitações. Entra a seção "Roteiro da demonstração facial", que cobre:
    - subir os três serviços;
    - "Usar imagem" com `demo/rostos-teste/ALU-900N-teste.jpg`, que autoriza o aluno certo;
    - `desconhecido.jpg`, que bloqueia;
    - cadastrar o próprio rosto em um aluno novo e ser reconhecido.
  - **Front:** tabela de motivos e "Usar imagem".
  - Tirar as linhas "em desenho" das pendências.
- [ ] **Step 4:** `grep -rn "1:1\|verificacoes\|ROSTO_NAO_CONFERE\|ALUNO_SEM_ROSTO" */README.md api-merenda-escolar/*.md`. Esperado: só ocorrências históricas, explicitamente marcadas como tal.

### Task 11: Validação ponta a ponta

- [ ] **Step 1:** rodar as três suítes completas (Python, `eval $MVN -B test`, front). Esperado: todas PASS. Anote as contagens.
- [ ] **Step 2: subir os serviços** com os comandos dos READMEs: analytics na 8000, API com `eval $MVN -B spring-boot:run` na 8081 e front na 4200. No log da API, conferir a carga dos 5 alunos de exemplo. Em `GET /api/v1/alunos`, os 5 `ALU-900N` aparecem com `temRostoCadastrado: true`.
- [ ] **Step 3: chamar a API direto.** Para cada imagem, montar `data:image/jpeg;base64,$(base64 -w0 arquivo)` e fazer `POST /api/v1/fila/validacoes` com `{"metodoIdentificacao":"FACIAL","fotoBase64":...}`. Esperado:
  - `ALU-900N-teste.jpg` dá `AUTORIZADO` com o `alunoNome` certo;
  - `desconhecido.jpg` dá `BLOQUEADO`/`ROSTO_NAO_RECONHECIDO`;
  - repetir `ALU-9001-teste.jpg` em menos de 2 min dá `CONSUMO_RECENTE_BLOQUEADO`;
  - `POST /api/v1/alunos/{id de ALU-001}/rosto` com `ALU-9002-teste.jpg` dá 409 citando `ALU-9002`.
- [ ] **Step 4: conferir no navegador.** Pedido do Frank: carregar a skill `betha-chrome-devtools:navegador` e seguir o fluxo dela, que usa o MCP `chrome-devtools`, conecta ao Chrome e age por snapshot. O Playwright MCP com `--executable-path /usr/bin/brave-browser` fica como reserva. Na fila, em modo facial, "Usar imagem" com `ALU-9003-teste.jpg` mostra o card verde com o nome que a semente final dá ao `ALU-9003` (hoje "Rafael Moreira"; conferir em `src/main/resources/demo/rostos-demo.json`). `desconhecido.jpg` mostra a mensagem de QR Code.
- [ ] **Step 5:** resumo para o Frank:
  - `git status` e `git diff --stat` dos três repositórios;
  - contagem de testes;
  - números de calibração;
  - o que ficou com ele: teste com a webcam e o próprio rosto, e os commits, perguntando o ticket.
