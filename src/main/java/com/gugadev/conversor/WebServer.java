package com.gugadev.conversor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gugadev.conversor.model.PairConversionResponse;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP embutido que expõe endpoints REST para conversão de moedas
 * e serve os arquivos estáticos da interface frontend.
 *
 * <h2>Endpoints disponíveis:</h2>
 * <ul>
 *   <li>{@code POST /api/convert} — converte um valor entre duas moedas</li>
 *   <li>{@code GET  /api/rate}    — consulta a taxa de câmbio entre duas moedas</li>
 *   <li>{@code GET  /api/currencies} — lista as moedas suportadas (com cache)</li>
 *   <li>{@code GET  /}            — serve arquivos estáticos do diretório {@code frontend/}</li>
 * </ul>
 *
 * @author gugadev
 */
public class WebServer {

    private static final Path FRONTEND_DIR = Path.of("frontend").toAbsolutePath().normalize();
    private static final Duration CURRENCIES_CACHE_TTL =
            Duration.ofHours(AppConfig.getInt("currencies.cache.ttl.hours", 6));
    private static final int THREAD_POOL_SIZE = 8;
    private static final int HTTP_BACKLOG = 0;

    private final ExchangeRateClient client;
    private final ObjectMapper objectMapper;
    private final int port;
    private volatile List<Map<String, String>> currenciesCache;
    private volatile Instant currenciesCacheAt;

    /**
     * Cria uma nova instância do servidor web.
     *
     * @param apiKey chave de acesso à ExchangeRate-API; não pode ser nula ou vazia
     * @param port   porta TCP para escutar conexões (1–65535)
     * @throws IllegalArgumentException se {@code apiKey} for nula/vazia ou {@code port} estiver fora do intervalo
     */
    public WebServer(String apiKey, int port) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey não pode ser nulo ou vazio.");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Porta deve estar no intervalo 1-65535.");
        }

        this.client = new ExchangeRateClient(apiKey);
        this.objectMapper = new ObjectMapper();
        this.port = port;
    }

    /**
     * Inicia o servidor HTTP e registra os handlers de cada endpoint.
     *
     * @throws IOException se não for possível criar o socket do servidor
     */
    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), HTTP_BACKLOG);
        server.createContext("/api/convert", new ConvertHandler());
        server.createContext("/api/rate", new RateHandler());
        server.createContext("/api/currencies", new CurrenciesHandler());
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(Executors.newFixedThreadPool(THREAD_POOL_SIZE));
        server.start();

        System.out.printf("Servidor iniciado em http://localhost:%d%n", port);
    }

    /**
     * Handler para o endpoint {@code GET /api/currencies}.
     * Retorna a lista de moedas suportadas com cache configurável.
     */
    private class CurrenciesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) {
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 405, Map.of("message", "Método não permitido"));
                return;
            }

            try {
                List<Map<String, String>> currencies = getCurrenciesWithCache();
                writeJson(exchange, 200, Map.of("currencies", currencies));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                writeJson(exchange, 500, Map.of("message", "Requisição interrompida"));
            } catch (IOException ex) {
                writeApiProviderError(exchange, ex);
            }
        }
    }

    /**
     * Handler para o endpoint {@code POST /api/convert}.
     * Recebe um JSON com {@code from}, {@code to} e {@code amount} e retorna
     * o resultado da conversão.
     */
    private class ConvertHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) {
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 405, Map.of("message", "Método não permitido"));
                return;
            }

            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                ConversionRequest request = objectMapper.readValue(body, ConversionRequest.class);

                String from = normalizeCurrencyCode(request.from());
                String to = normalizeCurrencyCode(request.to());
                double amount = request.amount();

                if (from == null || to == null || amount <= 0) {
                    writeJson(exchange, 400, Map.of("message", "Dados inválidos. Informe moedas e valor maior que zero."));
                    return;
                }

                PairConversionResponse result = client.convert(from, to, amount);
                if (!"success".equalsIgnoreCase(result.result())) {
                    writeJson(exchange, 502, Map.of("message", "Falha na API de câmbio", "errorType", result.errorType()));
                    return;
                }

                writeJson(exchange, 200, Map.of(
                        "baseCode", result.baseCode(),
                        "targetCode", result.targetCode(),
                        "conversionRate", result.conversionRate(),
                        "conversionResult", result.conversionResult(),
                        "amount", amount
                ));
            } catch (JsonProcessingException ex) {
                writeJson(exchange, 400, Map.of("message", "JSON inválido no corpo da requisição"));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                writeJson(exchange, 500, Map.of("message", "Requisição interrompida"));
            } catch (IOException ex) {
                writeApiProviderError(exchange, ex);
            }
        }
    }

    /**
     * Handler para o endpoint {@code GET /api/rate}.
     * Recebe os parâmetros {@code from} e {@code to} via query string e retorna
     * a taxa de câmbio atual.
     */
    private class RateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) {
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 405, Map.of("message", "Metodo nao permitido"));
                return;
            }

            Map<String, String> queryParams = parseQuery(exchange.getRequestURI().getRawQuery());
            String from = normalizeCurrencyCode(queryParams.get("from"));
            String to = normalizeCurrencyCode(queryParams.get("to"));

            if (from == null || to == null) {
                writeJson(exchange, 400, Map.of("message", "Informe from e to na query string."));
                return;
            }

            if (from.equals(to)) {
                writeJson(exchange, 200, Map.of(
                        "baseCode", from,
                        "targetCode", to,
                        "conversionRate", 1.0,
                        "updatedAt", Instant.now().toString()
                ));
                return;
            }

            try {
                PairConversionResponse result = client.convert(from, to, 1);
                if (!"success".equalsIgnoreCase(result.result())) {
                    writeJson(exchange, 502, Map.of("message", "Falha na API de cambio", "errorType", result.errorType()));
                    return;
                }

                writeJson(exchange, 200, Map.of(
                        "baseCode", result.baseCode(),
                        "targetCode", result.targetCode(),
                        "conversionRate", result.conversionRate(),
                        "updatedAt", Instant.now().toString()
                ));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                writeJson(exchange, 500, Map.of("message", "Requisição interrompida"));
            } catch (IOException ex) {
                writeApiProviderError(exchange, ex);
            }
        }
    }

    /**
     * Handler para servir arquivos estáticos do diretório {@code frontend/}.
     * Requisições para {@code /} são redirecionadas para {@code index.html}.
     */
    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            String rawPath = exchange.getRequestURI().getPath();
            String relativePath = "/".equals(rawPath) ? "index.html" : rawPath.substring(1);

            Path targetFile = FRONTEND_DIR.resolve(relativePath).normalize();
            if (!targetFile.startsWith(FRONTEND_DIR) || !Files.exists(targetFile) || Files.isDirectory(targetFile)) {
                writeText(exchange, 404, "Arquivo não encontrado", "text/plain; charset=utf-8");
                return;
            }

            byte[] content = Files.readAllBytes(targetFile);
            String contentType = Files.probeContentType(targetFile);
            if (contentType == null) {
                contentType = inferContentType(targetFile.getFileName().toString());
            }

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.close();
        }
    }

    /**
     * Serializa o payload como JSON e envia a resposta HTTP.
     *
     * @param exchange   o contexto da requisição HTTP
     * @param statusCode o código de status HTTP
     * @param payload    o objeto a ser serializado como JSON
     * @throws IOException em caso de erro de escrita
     */
    private void writeJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(payload);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");

        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    /**
     * Envia uma resposta HTTP com corpo de texto simples.
     *
     * @param exchange    o contexto da requisição HTTP
     * @param statusCode  o código de status HTTP
     * @param message     o texto da resposta
     * @param contentType o valor do header {@code Content-Type}
     * @throws IOException em caso de erro de escrita
     */
    private void writeText(HttpExchange exchange, int statusCode, String message, String contentType) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    /**
     * Normaliza um código de moeda para letras maiúsculas sem espaços.
     *
     * @param code código da moeda informado pelo usuário
     * @return o código normalizado ou {@code null} se estiver em branco
     */
    private String normalizeCurrencyCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        return code.trim().toUpperCase(Locale.US);
    }

    /**
     * Adiciona os headers CORS à resposta.
     *
     * @param headers os headers da resposta HTTP
     */
    private void addCorsHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    /**
     * Verifica se a requisição é um preflight CORS ({@code OPTIONS}) e,
     * em caso positivo, envia a resposta {@code 204 No Content}.
     *
     * @param exchange o contexto da requisição HTTP
     * @return {@code true} se foi um preflight e a resposta já foi enviada
     * @throws IOException em caso de erro de escrita
     */
    private boolean handleCorsPreflight(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange.getResponseHeaders());

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }

        return false;
    }

    /**
     * Faz o parsing de uma query string HTTP em um mapa chave-valor.
     *
     * @param rawQuery a query string bruta (sem o {@code ?} inicial)
     * @return mapa com os parâmetros decodificados
     */
    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return query;
        }

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (pair.isBlank()) {
                continue;
            }

            String[] keyValue = pair.split("=", 2);
            String key = decodeQueryComponent(keyValue[0]);
            String value = keyValue.length > 1 ? decodeQueryComponent(keyValue[1]) : "";
            query.put(key, value);
        }

        return query;
    }

    /**
     * Decodifica um componente de query string em formato URL-encoded.
     *
     * @param value o componente codificado
     * @return o valor decodificado em UTF-8
     */
    private String decodeQueryComponent(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * Envia uma resposta de erro {@code 502 Bad Gateway} indicando falha
     * na comunicação com a API externa.
     *
     * @param exchange o contexto da requisição HTTP
     * @param ex       a exceção capturada
     * @throws IOException em caso de erro de escrita
     */
    private void writeApiProviderError(HttpExchange exchange, IOException ex) throws IOException {
        writeJson(exchange, 502, Map.of("message", "Erro ao consultar API externa", "detail", ex.getMessage()));
    }

    /**
     * Retorna a lista de moedas suportadas, utilizando cache em memória
     * com TTL configurável pela propriedade {@code currencies.cache.ttl.hours}.
     *
     * @return lista de mapas contendo {@code code} e {@code name} de cada moeda
     * @throws IOException          em caso de erro de comunicação com a API
     * @throws InterruptedException se a requisição for interrompida
     */
    private synchronized List<Map<String, String>> getCurrenciesWithCache() throws IOException, InterruptedException {
        if (currenciesCache != null
                && currenciesCacheAt != null
                && currenciesCacheAt.plus(CURRENCIES_CACHE_TTL).isAfter(Instant.now())) {
            return currenciesCache;
        }

        List<ExchangeRateClient.CurrencyInfo> apiCurrencies = client.getSupportedCurrencies();
        List<Map<String, String>> mappedCurrencies = new ArrayList<>();

        for (ExchangeRateClient.CurrencyInfo currency : apiCurrencies) {
            mappedCurrencies.add(Map.of(
                    "code", currency.code(),
                    "name", currency.name().isBlank() ? currency.code() : currency.name()
            ));
        }

        currenciesCache = mappedCurrencies;
        currenciesCacheAt = Instant.now();
        return mappedCurrencies;
    }

    /**
     * Infere o tipo MIME de um arquivo com base na sua extensão.
     *
     * @param fileName nome do arquivo (ex.: "index.html")
     * @return o tipo MIME correspondente ou {@code application/octet-stream} como fallback
     */
    private String inferContentType(String fileName) {
        if (fileName.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (fileName.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (fileName.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        return "application/octet-stream";
    }

    /**
     * Representa o corpo JSON de uma requisição de conversão.
     *
     * @param from   código da moeda de origem
     * @param to     código da moeda de destino
     * @param amount valor a ser convertido
     */
    private record ConversionRequest(String from, String to, double amount) {
    }
}