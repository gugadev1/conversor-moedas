# Conversor de Moedas

Aplicação de conversão de moedas com backend Java puro e interface web.

## Visão geral

O projeto possui dois modos:

- **Web (padrão):** inicia um servidor HTTP embutido, serve a interface frontend e expõe endpoints REST de conversão, cotação e listagem de moedas.
- **CLI (opcional):** fluxo interativo no terminal para conversão manual.

A API externa utilizada é a [ExchangeRate-API](https://www.exchangerate-api.com/).

## Funcionalidades

- Conversão de moedas em tempo real.
- Exibição da cotação atual do par selecionado.
- Lista completa de moedas suportadas carregada dinamicamente da API (com cache de 6 horas).
- Tema claro/escuro com persistência no navegador (`localStorage`).
- Fallback no frontend para opções padrão de moedas caso a listagem fique indisponível.

## Stack

- Java 21
- Maven
- `java.net.http.HttpClient` (JDK — sem frameworks externos)
- Jackson (`jackson-databind`)
- JUnit 5 + Mockito (testes)
- HTML, CSS e JavaScript (vanilla)

## Estrutura do projeto

```txt
frontend/
  index.html
  styles.css
  app.js
src/main/java/com/gugadev/conversor/
  Main.java
  AppConfig.java
  ExchangeRateClient.java
  WebServer.java
  handler/
    BaseHandler.java
    ConvertHandler.java
    CurrenciesHandler.java
    RateHandler.java
    StaticFileHandler.java
  model/
    PairConversionResponse.java
    SupportedCodesResponse.java
src/main/resources/
  application.properties
pom.xml
```

## Configuração

### 1. Crie sua API key

1. Acesse: `https://www.exchangerate-api.com/`
2. Crie uma conta
3. Copie sua chave

### 2. Exporte a variável de ambiente

```bash
export EXCHANGE_RATE_API_KEY="sua_chave_aqui"
```

### 3. Propriedades da aplicação

O arquivo `application.properties` permite ajustar o comportamento sem recompilar:

```properties
api.baseUrl=https://v6.exchangerate-api.com/v6
server.port.default=8080
currencies.cache.ttl.hours=6
```

A variável de ambiente `PORT` tem prioridade sobre `server.port.default`.

## Como executar

### Modo web (padrão)

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

## Endpoints

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

Retorna a cotação atual do par.

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

Retorna a lista de moedas suportadas. Resultado cacheado em memória por 6 horas para reduzir chamadas externas.

Resposta (sucesso):

```json
{
  "currencies": [
    { "code": "BRL", "name": "Brazilian Real" },
    { "code": "USD", "name": "United States Dollar" }
  ]
}
```

## Comportamento de erros

- Requisições inválidas retornam `400`.
- Métodos HTTP não permitidos retornam `405`.
- Falhas na API externa retornam `502` com mensagem de detalhe.
- Interrupções de execução retornam `500`.

## Desenvolvimento

Compilar:

```bash
mvn compile
```

Executar testes:

```bash
mvn test
```


```bash
mvn compile exec:java
```

