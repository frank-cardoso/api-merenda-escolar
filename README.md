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

```powershell
$env:GEMINI_API_KEY = "sua-chave"
$env:GEMINI_MODEL = "gemini-3.6-flash"
mvn spring-boot:run "-Dspring-boot.run.profiles=gemini"
```

O modelo padrao do profile `gemini` e `gemini-3.6-flash`. Em contas ou projetos novos, o Google pode retornar HTTP 404 para `gemini-2.5-flash` com a mensagem de que o modelo nao esta mais disponivel para novos usuarios.

A IA fica desativada por padrao para que a fila funcione sem credenciais ou internet. Somente informacoes agregadas podem ser enviadas ao modelo. Nomes, matriculas, identificadores e biometria nao fazem parte do payload de IA.

Sem o profile `gemini`, a API usa um adapter fake deterministico. Ele existe para testes e demonstracoes offline; a integracao real e selecionada exclusivamente pelo profile.

O tier gratuito e adequado ao prototipo e nao deve ser tratado como capacidade garantida de producao. Os limites efetivos variam por projeto e modelo e devem ser consultados no Google AI Studio. No tier gratuito, o Google informa que o conteudo pode ser usado para melhorar seus produtos; por isso, este projeto envia somente indicadores agregados e nunca dados identificaveis ou biometricos.

## Relatorio logistico

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
