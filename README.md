# Conversor de Moedas (Java + ExchangeRate API)

Aplicacao de conversao de moedas com backend Java e interface web.

## Visao geral

O projeto possui dois modos:

- Modo web (padrao): inicia servidor HTTP, serve a UI e expoe endpoints de conversao/cotacao/moedas.
- Modo CLI (opcional): fluxo no terminal para conversao manual.

A API externa utilizada e a [ExchangeRate-API](https://www.exchangerate-api.com/).

## Funcionalidades

- Conversao de moedas em tempo real.
- Exibicao de cotacao atual do par selecionado.
- Lista completa de moedas suportadas carregada dinamicamente da API.
- Tema claro/escuro com persistencia no navegador (`localStorage`).
- Fallback no frontend para opcoes padrao de moedas caso a listagem de moedas fique indisponivel.

## Stack

- Java 17
- Maven
- `java.net.http.HttpClient`
- Jackson (`jackson-databind`)
- HTML, CSS e JavaScript (vanilla)

## Estrutura do projeto

```txt
frontend/
  index.html
  styles.css
  app.js
src/main/java/com/gugadev/conversor/
  Main.java
  ExchangeRateClient.java
  WebServer.java
  model/
    PairConversionResponse.java
    SupportedCodesResponse.java
pom.xml
```

## Configuracao

### 1. Crie sua API key

1. Acesse: `https://www.exchangerate-api.com/`
2. Crie uma conta
3. Copie sua chave

### 2. Exporte a variavel de ambiente

Linux/macOS:

```bash
export EXCHANGE_RATE_API_KEY="sua_chave_aqui"
```

## Como executar

### Modo web (padrao)

```bash
mvn compile exec:java
```

Acesse: `http://localhost:8080`

Porta customizada:

```bash
PORT=9090 mvn compile exec:java
```

### Modo CLI (opcional)

```bash
mvn compile exec:java -Dexec.args="--cli"
```

## Endpoints internos

### `POST /api/convert`

Converte um valor entre duas moedas.

Exemplo de body:

```json
{
  "from": "USD",
  "to": "BRL",
  "amount": 100
}
```

Resposta (sucesso):

```json
{
  "baseCode": "USD",
  "targetCode": "BRL",
  "conversionRate": 5.12,
  "conversionResult": 512.0,
  "amount": 100.0
}
```

### `GET /api/rate?from=USD&to=BRL`

Retorna a cotacao atual do par.

Resposta (sucesso):

```json
{
  "baseCode": "USD",
  "targetCode": "BRL",
  "conversionRate": 5.12,
  "updatedAt": "2026-03-06T00:00:00Z"
}
```

### `GET /api/currencies`

Retorna a lista de moedas suportadas pela ExchangeRate API.

Resposta (sucesso):

```json
{
  "currencies": [
    { "code": "USD", "name": "United States Dollar" },
    { "code": "BRL", "name": "Brazilian Real" }
  ]
}
```

Observacao: esse endpoint usa cache em memoria no backend por 6 horas para reduzir chamadas externas.

## Comportamento de erros

- Requisicoes invalidas retornam `400`.
- Metodos HTTP nao permitidos retornam `405`.
- Falhas na API externa retornam `502` com mensagem de detalhe.
- Interrupcoes de execucao retornam `500`.

## Desenvolvimento

Compilar:

```bash
mvn compile
```

Executar com logs do servidor:

```bash
mvn compile exec:java
```

