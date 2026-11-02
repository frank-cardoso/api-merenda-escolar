# Backlog — próximas features

## Linguagem voltada ao cliente final (levantado em 2026-10-19)

**Jargão interno vazando para o relatório.** O cliente final não sabe o que é "Java".
Ponto confirmado: `analytics-merenda-escolar/app/domain/previsao_consumo.py:108` monta a
evidência "A sobra de planejamento informada pela API Java foi de {n} refeições", e ela vai
direto para `evidencias` da previsão, que aparece no relatório.

Varrer também o prompt enviado ao modelo: se o payload ou as instruções citarem a origem
técnica, o texto gerado repete. As demais ocorrências de "Java" encontradas no código são
Javadoc interno e não chegam à tela.

## Dashboard mais limpo (levantado em 2026-10-19)

Informação demais na tela para o cliente final. Hoje o dashboard carrega avisos
metodológicos que existem para a análise não afirmar mais do que pode provar:

- "execução registrada não comprova ingestão"
- "bloqueios não representam rejeição"
- "mínimo de 5 planejamentos por item"
- ressalvas de amostra insuficiente e de dados sintéticos

Não é bug, é decisão de produto: enxugar sem perder a ressalva. Cortar tudo transforma o
dashboard em algo que afirma mais do que mede. Precisa de brainstorm com o Frank decidindo
o que é visão primária e o que vira detalhe sob demanda.
