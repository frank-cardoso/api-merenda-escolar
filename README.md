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

- API: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui.html`
- Health check: `http://localhost:8080/actuator/health`

O H2 utiliza o arquivo local `data/merenda.mv.db`, ignorado pelo Git. Nos testes, o banco e executado em memoria.

## Configuracao do Gemini

```powershell
$env:GEMINI_API_KEY = "sua-chave"
$env:GEMINI_MODEL = "gemini-2.5-flash"
./mvnw spring-boot:run -Dspring-boot.run.profiles=gemini
```

A IA fica desativada por padrao para que a fila funcione sem credenciais ou internet. Somente informacoes agregadas podem ser enviadas ao modelo. Nomes, matriculas, identificadores e biometria nao fazem parte do payload de IA.

Consulte [implementation_plan.md](implementation_plan.md) para o roteiro completo.
