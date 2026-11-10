# Identificação facial na fila (1:N) e rostos de demonstração — desenho

Data: 2026-11-07. Repositórios: `api-merenda-escolar`, `analytics-merenda-escolar`, `front-merenda-escolar`.

## Intenção

Hoje a fila tem o modo "Reconhecimento facial", mas ele não reconhece ninguém: o operador digita o
código do aluno (o campo vem preenchido com `ALU-001`) e a câmera só confere 1:1 se o rosto bate com
o cadastro daquele aluno. O Frank quer o comportamento que o nome promete: abrir a fila, a pessoa
olha para a câmera e o sistema descobre sozinho quem é entre os alunos cadastrados e libera.

O motivo imediato é a apresentação do protótipo para o professor. Ela precisa funcionar de dois
jeitos:

1. **Ao vivo com o Frank:** ele cadastra o próprio rosto na tela do aluno ("Cadastrar rosto", que já
   existe) e em seguida é reconhecido na fila.
2. **Sem preparo, com alunos de exemplo:** ao subir o sistema já existem alunos de demonstração com
   rosto cadastrado, e há fotos de teste desses alunos (diferentes das de cadastro) para mostrar na
   fila. Isso deixa a demonstração reproduzível antes e durante a apresentação, sem depender de
   ninguém estar presente.

## Decisões do Frank

- Fila facial passa a ser **identificação 1:N** entre os alunos cadastrados, sem digitar código.
- Cadastro do rosto continua pela tela do aluno, como está.
- Alunos de exemplo sobem com rosto cadastrado.
- Rostos de exemplo vêm do conjunto público **LFW (Labeled Faces in the Wild)**: adultos, várias
  fotos por pessoa. Foto de criança real fica fora em qualquer hipótese.
- Ele confirmou o resumo: captura manual (botão "Capturar"), busca entre todos os alunos com rosto
  (não há entidade escola no protótipo), a verificação 1:1 com código deixa de existir, painel e
  auditoria não mudam (consumo segue registrado com método `FACIAL`).

## Metas

- Na fila, modo facial: capturar uma foto e receber AUTORIZADO com o nome do aluno certo, ou
  BLOQUEADO com motivo claro.
- Bloquear quando nenhum cadastrado for parecido o suficiente, e quando o melhor e o segundo melhor
  estiverem próximos demais (ambíguo). Nesses casos a tela orienta usar o QR Code.
- As regras de negócio existentes valem igual depois de identificado: aluno ativo, cardápio do
  turno, bloqueio de repetição de 2 minutos, carimbo de turma na auditoria.
- Alunos de demonstração com rosto, idempotentes entre reinícios (o H2 é em arquivo, `./data/merenda`).
- Documentação do projeto coerente com o código e com o que foi apurado (ver "Documentação").

## Fora de escopo

- Prova de vida (anti-spoofing). Foto de foto passa, e isso é sabido: é o que permite mostrar a foto
  de teste no celular para a webcam. Fica registrado como limitação.
- Entidade escola e filtro da busca por escola. Fica anotado como próximo passo.
- Captura contínua/automática.
- Índice vetorial (pgvector, FAISS). Com dezenas ou poucas centenas de cadastrados, comparação
  linear custa menos de 1 ms.
- Persistir foto em qualquer lugar. Continua só o embedding.
- Mudanças no dashboard, nos indicadores ou no fluxo do LLM.

## Anti-metas (falha mesmo que "funcione")

- Liberar o aluno errado com confiança quando há dúvida: em caso de ambiguidade, bloqueia e pede QR.
- Deixar a demonstração frágil: o primeiro reconhecimento de um aluno de exemplo não pode sair
  bloqueado por "consumo recente" vindo do histórico sintético.
- Usar a mesma imagem no cadastro e no teste de um aluno de exemplo: a distância daria quase zero e
  a demonstração pareceria forçada.
- Foto ou embedding de pessoa real em teste automatizado (regra do Frank: teste nunca carrega dado
  real; commit é permanente). As imagens LFW existem só como material de demonstração.
- A fila passar a depender do LLM. Continua síncrona e sem IA generativa.

## Restrições

- **Python não é dono de tabela** (princípio do `implementation_plan.md`): tudo o que ele precisa
  chega no pedido. Os embeddings dos candidatos vão do Java para o Python na requisição.
- Mesmo modelo e detector do cadastro existente: `Facenet` (128 dimensões) com `detector_backend="opencv"`.
  Embedding de cadastro e de busca têm de sair do mesmo modelo, senão a distância não significa nada.
- A chamada ao serviço Python **não deve ficar dentro da transação** de validação. Hoje
  `conferirRosto` roda dentro de `validarDentroDaTransacao`, segurando conexão durante a chamada
  remota. Na 1:N a identificação acontece antes de saber quem é o aluno, então ela vai para fora da
  transação, e a transação recebe o aluno já identificado.
- Aluno não identificado não grava auditoria, igual ao comportamento atual de
  `ALUNO_NAO_ENCONTRADO_OU_INATIVO` (não há aluno para associar à linha).
- Git: nada de `git add`/`commit`/`push` sem o Frank pedir. Ele revisa o resumo antes. Commits
  nesses repositórios seguem o padrão já usado (`feat(api): ...`, `fix(front): ...`), em pt-BR.

## Como funciona (decidido)

### Fluxo da fila, modo facial

1. Front envia `POST /api/v1/fila/validacoes` com `metodoIdentificacao: "FACIAL"` e `fotoBase64`,
   **sem** `alunoCodigo`.
2. API carrega os alunos ativos com `rosto_embedding_json` preenchido (candidatos).
3. API chama o Python num endpoint novo de identificação, mandando a foto e a lista de candidatos
   (id + embedding).
4. Python gera o embedding da foto, calcula a distância para cada candidato e devolve o melhor
   candidato, a distância dele, a do segundo e a decisão.
5. Com aluno identificado, a API segue o fluxo atual (cardápio, repetição, auditoria).
6. Resposta igual à de hoje (`alunoId`, `alunoNome`, `turno`, `refeicao`, `motivo`...), de modo que o
   card de resultado mostra quem foi reconhecido.

Para `QR_CODE`, `alunoCodigo` continua obrigatório. A validação do request passa a ser condicional
ao método.

### Decisão de identificação

- Reconhecido quando a menor distância fica abaixo do limiar do modelo **e** a diferença para o
  segundo colocado é pelo menos a margem configurada.
- Com um único candidato, vale só o limiar.
- Motivos de bloqueio novos (traduzidos no `resultado-validacao.component.ts`):
  - `ROSTO_NAO_RECONHECIDO`: nenhum cadastrado abaixo do limiar.
  - `ROSTO_AMBIGUO`: melhor e segundo próximos demais.
  - `NENHUM_ROSTO_CADASTRADO`: não há candidato algum.
  - Mantidos: `FOTO_NAO_INFORMADA`, `ROSTO_NAO_DETECTADO`.
  - Saem: `ROSTO_NAO_CONFERE`, `ALUNO_SEM_ROSTO_CADASTRADO` (eram da 1:1).
- A mensagem dos motivos novos orienta usar o QR Code.

### Cadastro recusa rosto já cadastrado em outro aluno

Decisão do Frank. Sem isso, o mesmo rosto em dois alunos faz a fila responder sempre "ambíguo"
para essa pessoa.

- Em "Cadastrar rosto", antes de gravar, a API roda a mesma identificação da fila. A foto nova é
  comparada com os embeddings dos **outros** alunos ativos.
- Recadastrar o rosto do próprio aluno continua permitido.
- Se algum outro aluno ficar abaixo do limiar, o cadastro é recusado e nada é gravado. A
  resposta é um erro de conflito, no mesmo padrão `ProblemDetail` das demais rejeições de cadastro
  (há `ConflitoCadastroException`), e informa o código do aluno que já tem esse rosto.
- A tela do aluno mostra a mensagem.
- Nesta checagem, ambiguidade entre outros alunos não altera nada: qualquer outro abaixo do
  limiar já basta para recusar.

### Remoção da 1:1

Sem consumidor depois da mudança, sai inteira: `ReconhecimentoFacialPort.verificar`, o endpoint
Python `/reconhecimento-facial/verificacoes` com `VerificarRosto*`, o campo "Código do aluno" da
fila e o texto "Confirma 1:1 contra o rosto já cadastrado...", e os testes correspondentes. Os
comentários e Javadocs que afirmam "nunca busca entre todos (1:N)" (no port, no módulo Python, no
`ValidarConsumoRequest`, no `fila-page.component.ts`) são reescritos, porque passam a ser falsos.

### Rostos de demonstração

- Um script no `analytics-merenda-escolar` (por exemplo `scripts/gerar_rostos_demo.py`) prepara o
  material uma vez, fora da subida da aplicação:
  - escolhe identidades LFW com pelo menos 2 imagens;
  - gera o embedding da imagem de cadastro com o mesmo modelo/detector;
  - **confere** que a imagem de teste de cada um é reconhecida como ele, com margem, contra os demais
    do conjunto; identidade que não passa é trocada;
  - grava um JSON de semente consumido pela API (nome fictício, código, embedding, modelo) e copia as
    imagens de teste para uma pasta de demonstração.
- Composição: 5 alunos de exemplo com rosto, mais 1 identidade LFW **não cadastrada** para mostrar o
  caso negativo.
- Os alunos de exemplo recebem nomes fictícios, não o nome da pessoa do LFW, e são enturmados nas
  turmas de demonstração existentes.
- A API carrega a semente na subida, idempotente: cria os alunos se não existirem e preenche o
  embedding só se estiver vazio. Não depende do serviço Python estar no ar, porque o embedding já
  vem pronto no JSON.
- Escolha do Claude, para a primeira leitura na apresentação autorizar. Vale para os alunos semeados
  e para o aluno em que o Frank cadastrar o próprio rosto:
  - **Todo aluno com rosto cadastrado**, semeado ou não, fica fora da geração de consumo sintético
    do `DadosDemonstracaoLoader`. Hoje o loader gera, a cada subida em dia útil, consumos AUTORIZADO
    do dia para todos os alunos existentes, em horários perto de 9h, 12h e 14h.
  - O loader **não gera consumo com instante posterior a `agora`**. Hoje ele gera, e a checagem de
    repetição (`instante >= agora - 2m`, sem limite superior) conta essas linhas futuras como consumo
    recente. A regra de repetição em si não muda.
  - O loader garante cardápio de **hoje em todos os turnos**, inclusive sábado e domingo. Hoje fora
    de dia útil só NOITE ganha cardápio, e MANHÃ/TARDE devolveriam `CARDAPIO_NAO_CONFIGURADO` se a
    apresentação ou o ensaio caírem num fim de semana.
- Base local conferida em 2026-11-07 (`GET /api/v1/alunos`): 150 alunos, nenhum com rosto
  cadastrado. Não há resíduo do teste da 1:1 para limpar.
- **Vai para o git** (decisão do Frank): as imagens de teste e a semente com os embeddings ficam
  commitadas, para que um clone novo, inclusive na máquina do professor, suba com a demonstração
  pronta. O script gerador também fica commitado, mas não roda na subida.
- Pasta de demonstração com as imagens de teste e um `README` curto: de onde vêm as imagens (LFW,
  com a citação do conjunto), qual imagem corresponde a qual aluno de exemplo e qual é o
  "desconhecido".

### Entrada por arquivo ("Usar imagem")

Confirmado pelo Frank: vale na fila e no cadastro.

O `CameraFotoComponent` ganha, ao lado de "Iniciar câmera", a opção de escolher um arquivo de
imagem. Ela emite o mesmo data URL base64 que a captura pela webcam. Serve de reserva se a webcam
ou a luz da sala atrapalharem, e para rodar a demonstração com as imagens de teste. Por estar no
componente compartilhado, também vale no "Cadastrar rosto".

### Limpeza no caminho

`ngAfterViewInit` vazio do `camera-foto.component.ts` (só tem um comentário) sai, junto com o
`AfterViewInit` do `implements`. O comentário explica uma regra que pertence a `iniciarCamera`.

## Documentação

Hoje a documentação afirma que reconhecimento facial está fora de escopo, e o código já o tem.
Também contém um erro de fato apurado nesta conversa.

- `DECISOES-TECNICAS.md` §8 ("Identificação fica no QR Code"): reescrever.
  - **Correção de fato:** pela documentação do time (`Educa/documentacao/documentacao/reconhecimento-facial/`,
    diagramas `arquitetura.png` e `processo-atualizar-foto-aluno.png`) e pela análise do ED-40538,
    `betha-bio-sdk` não é alternativa ao `attendance-api`/`educacao-attendance-worker`. É a ponta da
    escola da **mesma cadeia**: aparelho Intelbras reconhece localmente, o agente envia os registros
    para `attendance-api`, que publica no SNS/SQS, e o worker grava `attendance_lots` e dispara os
    scripts de presença e de notificação aos responsáveis. O worker foi corrigido e validado em
    produção em 2026-08-25 (ED-40538). A "aposentadoria" do serviço não foi confirmada por nenhuma
    fonte encontrada; o texto não deve afirmá-la.
  - **Decisão nova:** na merenda, a fila é contagem supervisionada (a merendeira está presente, o
    que faz o papel de prova de vida) e errar para mais custa pouco, enquanto recusar a criança certa
    custa caro. Por isso tablet/notebook com webcam e comparação no servidor é adequado ao caso. O
    terminal com infravermelho (Intelbras, faixa de R$ 2 mil a R$ 6 mil por ponto em anúncios de
    loja) é o caminho para presença automática sem supervisão.
  - **O que faltaria para produção:** prova de vida se não houver supervisão; recadastro periódico
    (rosto de criança muda); piloto medindo falso aceite e falsa recusa com crianças para calibrar
    limiar e margem; LGPD (biometria é dado sensível, art. 5º II e art. 11; dado de criança exige
    consentimento específico de um responsável, art. 14; o embedding também é biometria), com
    alternativa sem biometria para quem não consentir, RIPD, criptografia, prazo de retenção e
    exclusão; falha de reconhecimento nunca nega refeição (cai para QR ou confirmação do
    funcionário); busca restrita à escola.
- `implementation_plan.md`: seção "Identificação", fase 3 (deixa de ser "Descartada"), modelo
  (`Aluno` com `rosto_embedding_json`; `BiometriaFacial` "não será implementado" deixa de valer),
  contratos REST da fila, critérios de aceite da identificação.
- `front-merenda-escolar/README.md`: remove `face-api.js` e o reconhecimento local no navegador;
  descreve o modo facial atual e o "Usar imagem".
- READMEs da API e do analytics: endpoint novo de identificação e o roteiro da demonstração (como
  usar as imagens de teste).

## Validação esperada

- Testes automatizados nos três repositórios, com o DeepFace e o serviço Python simulados (sem
  imagem real): decisão de identificação (reconhecido, não reconhecido, ambíguo, candidato único,
  nenhum candidato, rosto não detectado), recusa de rosto duplicado no cadastro (e recadastro do
  próprio aluno permitido), request condicional ao método, carga idempotente da
  semente, alunos de demonstração fora do consumo sintético, tradução dos motivos novos, entrada por
  arquivo no componente.
- Build e testes passando em cada repositório, rodando os comandos do próprio projeto.
- Ponta a ponta com os três serviços no ar, pelo "Usar imagem": cada imagem de teste autoriza o
  aluno de exemplo certo; a imagem do "desconhecido" bloqueia com `ROSTO_NAO_RECONHECIDO`; repetir
  a mesma imagem em menos de 2 minutos bloqueia por consumo recente.
- Teste com a webcam e o rosto do Frank (cadastro e fila) fica com ele.

## Deixado para quem constrói

- Formato exato do endpoint de identificação no Python (nome, campos). Ele recebe a foto e os
  candidatos e devolve o resultado.
- Onde o limiar e a margem ficam configurados (o Python, que conhece a métrica do DeepFace, é o
  lugar natural) e os valores iniciais. Calibrar a margem com as distâncias medidas no próprio
  conjunto de demonstração, entre pares da mesma pessoa e de pessoas diferentes, e registrar os
  números medidos no `DECISOES-TECNICAS.md`.
- Foto da fila com mais de um rosto (criança de trás aparecendo): usar o maior rosto detectado, que é
  o de quem está na frente da câmera. Se o DeepFace não der a área, decidir o equivalente e registrar.
- Quais identidades LFW usar, desde que passem na conferência do script. Preferir pessoas pouco
  conhecidas.
- Onde ficam a semente JSON (provavelmente `src/main/resources` da API) e a pasta de imagens de
  teste, ambas commitadas.
- Forma de baixar o LFW no script (site oficial ou espelho), desde que o script registre a origem.
