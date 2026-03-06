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

public class WebServer {
    private static final Path FRONTEND_DIR = Path.of("frontend").toAbsolutePath().normalize();
    private static final Duration CURRENCIES_CACHE_TTL = Duration.ofHours(6);

    private final ExchangeRateClient client;
    private final ObjectMapper objectMapper;
    private final int port;
    private volatile List<Map<String, String>> currenciesCache;
    private volatile Instant currenciesCacheAt;

    public WebServer(String apiKey, int port) {
        this.client = new ExchangeRateClient(apiKey);
        this.objectMapper = new ObjectMapper();
        this.port = port;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/convert", new ConvertHandler());
        server.createContext("/api/rate", new RateHandler());
        server.createContext("/api/currencies", new CurrenciesHandler());
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.printf("Servidor iniciado em http://localhost:%d%n", port);
    }

    private class CurrenciesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange.getResponseHeaders());

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 405, Map.of("message", "Metodo nao permitido"));
                return;
            }

            try {
                List<Map<String, String>> currencies = getCurrenciesWithCache();
                writeJson(exchange, 200, Map.of("currencies", currencies));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                writeJson(exchange, 500, Map.of("message", "Requisicao interrompida"));
            } catch (IOException ex) {
                writeApiProviderError(exchange, ex);
            }
        }
    }

    private class ConvertHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange.getResponseHeaders());

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, 405, Map.of("message", "Metodo nao permitido"));
                return;
            }

            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                ConversionRequest request = objectMapper.readValue(body, ConversionRequest.class);

                String from = normalizeCurrencyCode(request.from());
                String to = normalizeCurrencyCode(request.to());
                double amount = request.amount();

                if (from == null || to == null || amount <= 0) {
                    writeJson(exchange, 400, Map.of("message", "Dados invalidos. Informe moedas e valor maior que zero."));
                    return;
                }

                PairConversionResponse result = client.convert(from, to, amount);
                if (!"success".equalsIgnoreCase(result.result())) {
                    writeJson(exchange, 502, Map.of("message", "Falha na API de cambio", "errorType", result.errorType()));
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
                writeJson(exchange, 400, Map.of("message", "JSON invalido no corpo da requisicao"));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                writeJson(exchange, 500, Map.of("message", "Requisicao interrompida"));
            } catch (IOException ex) {
                writeApiProviderError(exchange, ex);
            }
        }
    }

    private class RateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange.getResponseHeaders());

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
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
                writeJson(exchange, 500, Map.of("message", "Requisicao interrompida"));
            } catch (IOException ex) {
                writeApiProviderError(exchange, ex);
            }
        }
    }

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
                writeText(exchange, 404, "Arquivo nao encontrado", "text/plain; charset=utf-8");
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

    private void writeJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(payload);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");

        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void writeText(HttpExchange exchange, int statusCode, String message, String contentType) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private String normalizeCurrencyCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        return code.trim().toUpperCase(Locale.US);
    }

    private void addCorsHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

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

    private String decodeQueryComponent(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private void writeApiProviderError(HttpExchange exchange, IOException ex) throws IOException {
        writeJson(exchange, 502, Map.of("message", "Erro ao consultar API externa", "detail", ex.getMessage()));
    }

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

    private record ConversionRequest(String from, String to, double amount) {
    }
}
