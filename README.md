# Conversor de Moedas (Java + ExchangeRate API)

Projeto de estudo para aprender consumo de API em Java, com backend HTTP e frontend web para converter valores entre moedas.

## Como a API funciona neste projeto

1. O usuario informa:
- moeda de origem (ex: `USD`)
- moeda de destino (ex: `BRL`)
- valor (ex: `100`)

2. O app Java faz uma requisicao HTTP `GET` para este endpoint:

```txt
https://v6.exchangerate-api.com/v6/SUA_API_KEY/pair/USD/BRL/100
```

3. A API responde JSON com taxa e resultado da conversao.

4. O app mostra no terminal:
- taxa de conversao (`conversion_rate`)
- valor convertido (`conversion_result`)

## Tecnologias usadas

- Java 17
- Maven
- `java.net.http.HttpClient` (requisicoes HTTP)
- Jackson (`jackson-databind`) para converter JSON em objeto Java

## Estrutura

```txt
src/main/java/com/gugadev/conversor/
	Main.java
	ExchangeRateClient.java
	model/PairConversionResponse.java
pom.xml
```

## Como executar

### 1. Criar sua API Key

1. Acesse: `https://www.exchangerate-api.com/`
2. Crie uma conta
3. Copie sua chave

### 2. Exportar variavel de ambiente

No Linux/macOS:

```bash
export EXCHANGE_RATE_API_KEY="sua_chave_aqui"
```

### 3. Rodar o projeto (modo web)

```bash
mvn compile exec:java
```

Depois, acesse `http://localhost:8080`.

### 4. Rodar no modo console (opcional)

```bash
mvn compile exec:java -Dexec.args="--cli"
```

## Conceitos de API que este projeto cobre

- Endpoint
- Metodo HTTP (`GET`)
- Status code HTTP
- JSON de resposta
- Autenticacao com API key
- Tratamento de erros de rede e resposta da API

## Exemplo de uso

```txt
=== Conversor de Moedas (Java + ExchangeRate API) ===
Digite SAIR na moeda de origem para encerrar.

Moeda de origem:
USD
Moeda de destino:
BRL
Valor:
100
Taxa USD -> BRL: 5.120000
Resultado: 100.00 USD = 512.00 BRL
```

## Melhorias futuras

- Menu com opcoes pre-definidas
- Historico de conversoes
- Cache local de taxas
- Versao web (backend Java + frontend)