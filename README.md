# API — Controle de Merenda Escolar

Backend do prototipo academico de Controle de Merenda Escolar, desenvolvido com Java 25, Spring Boot 3.5, Spring Data JPA, H2, Flyway e Spring AI.

## Responsabilidades

- **Fila:** validacao sincrona de consumo por QR Code ou reconhecimento facial, sem IA.
- **Gestao:** consolidacao de consumo e geracao assincrona de relatorios logisticos com Gemini.

A integracao com IA permanece isolada atras de uma porta de aplicacao. Ela nao participa e nao pode ser dependencia do fluxo da fila.

## Execucao local

Pre-requisitos:

- JDK 25
- Maven 3.9+ ou Maven Wrapper
- Chave Gemini somente para o profile de IA real

```powershell
./mvnw spring-boot:run
```

- API: `http://localhost:8081`
- Swagger: `http://localhost:8081/swagger-ui.html`
- Health check: `http://localhost:8081/actuator/health`

O H2 utiliza o arquivo local `data/merenda.mv.db`, ignorado pelo Git. Nos testes, o banco e executado em memoria.

## Configuracao do Gemini

Por padrao, a API sobe em modo fake/offline. Nesse modo, o `application.yml` define:

```yaml
spring:
  ai:
    model:
      chat: none

merenda:
  ia:
    provedor: fake
    modelo: regras-locais-v1
```

Para usar Gemini real, ative o profile Spring `gemini`. O Spring Boot carrega automaticamente:

```text
application.yml
application-gemini.yml
```

O arquivo [application-gemini.yml](src/main/resources/application-gemini.yml) configura o Spring AI com `google-genai` e troca o provedor interno para `gemini`.

Este projeto tambem possui um profile Maven chamado `gemini`. Ele serve como atalho para ativar o profile Spring de mesmo nome durante o `spring-boot:run`.

Para desenvolvimento local, copie o exemplo Spring para um arquivo local ignorado pelo Git:

```powershell
Copy-Item src\main\resources\application-local.example.yml src\main\resources\application-local.yml
```

Depois edite `src/main/resources/application-local.yml` e preencha:

```yaml
spring:
  ai:
    google:
      genai:
        api-key: sua-chave-gemini
```

O `application-local.yml` real nao deve ser versionado. Ele fica ignorado pelo Git.

Com o arquivo local configurado, rode com o profile Maven `local`:

```powershell
.\mvnw.cmd spring-boot:run -Plocal
```

Esse profile Maven ativa os profiles Spring `gemini,local`. Assim o Spring carrega:

```text
application.yml
application-gemini.yml
application-local.yml
```

Se preferir nao criar arquivo local, use o profile Maven `gemini` e informe a chave por variavel de ambiente:

```powershell
$env:GEMINI_API_KEY = "sua-chave"
.\mvnw.cmd spring-boot:run -Pgemini
```

No IntelliJ, configure uma Run Configuration com:

- `Active profiles`: `gemini,local`
- `Environment variables`:
  - opcionalmente `GEMINI_API_KEY=sua-chave`, caso nao use `application-local.yml`

O modelo padrao do profile `gemini` e `gemini-3.6-flash`. Em contas ou projetos novos, o Google pode retornar HTTP 404 para `gemini-2.5-flash` com a mensagem de que o modelo nao esta mais disponivel para novos usuarios.

A IA fica desativada por padrao para que a fila funcione sem credenciais ou internet. Somente informacoes agregadas podem ser enviadas ao modelo. Nomes, matriculas, identificadores e biometria nao fazem parte do payload de IA.

Sem o profile `gemini`, a API usa um adapter fake deterministico. Ele existe para testes e demonstracoes offline; a integracao real e selecionada exclusivamente pelo profile.

O tier gratuito e adequado ao prototipo e nao deve ser tratado como capacidade garantida de producao. Os limites efetivos variam por projeto e modelo e devem ser consultados no Google AI Studio. No tier gratuito, o Google informa que o conteudo pode ser usado para melhorar seus produtos; por isso, este projeto envia somente indicadores agregados e nunca dados identificaveis ou biometricos.

## Relatorio logistico

Durante a geracao do relatorio, a API Java consolida os dados da fila, chama o servico Python de analytics e envia o resultado como insumo adicional para a IA:

```text
RelatorioIAWorker
  -> ConsolidacaoConsumoService
  -> Python Analytics /api/v1/previsoes-consumo
  -> Gemini via Spring AI
  -> RelatorioIA persistido
```

O servico Python e acessado por padrao em `http://localhost:8000`, configuravel por `MERENDA_ANALYTICS_BASE_URL`. O timeout padrao e `2s`, configuravel por `MERENDA_ANALYTICS_TIMEOUT`.

Se o servico Python estiver indisponivel, o relatorio continua sendo gerado com os dados consolidados em Java. Nesse caso, o input enviado para a IA recebe `previsaoConsumo: null` e `avisoPrevisaoConsumo` com a falha segura. Isso evita que uma API auxiliar derrube a demonstracao.

Primeiro consulte a consolidacao deterministica:

```http
GET /api/v1/gestao/consolidacoes?data=2026-08-26&turno=MANHA
```

Crie o job assincrono de analise:

```http
POST /api/v1/relatorios-ia
Content-Type: application/json

{
  "dataReferencia": "2026-08-26",
  "turno": "MANHA"
}
```

A resposta HTTP `202 Accepted` informa o `relatorioId` e a URL de acompanhamento. Consulte-a ate o status chegar a `CONCLUIDO` ou `FALHOU`:

```http
GET /api/v1/relatorios-ia/{relatorioId}
```

Os estados persistidos sao `PENDENTE`, `PROCESSANDO`, `CONCLUIDO` e `FALHOU`. O processamento ocorre em executor separado e nao compartilha o caminho critico da fila.

Consulte [implementation_plan.md](implementation_plan.md) para o roteiro completo.
