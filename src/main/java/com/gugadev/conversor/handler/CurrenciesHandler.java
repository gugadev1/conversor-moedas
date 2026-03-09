package com.gugadev.conversor.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gugadev.conversor.AppConfig;
import com.gugadev.conversor.ExchangeRateClient;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handler para o endpoint {@code GET /api/currencies}.
 * Retorna a lista de moedas suportadas com cache configuravel.
 */
public class CurrenciesHandler implements HttpHandler {

    private static final Duration CURRENCIES_CACHE_TTL =
            Duration.ofHours(AppConfig.getInt("currencies.cache.ttl.hours", 6));

    private final ExchangeRateClient client;
    private final ObjectMapper objectMapper;
    private volatile List<Map<String, String>> currenciesCache;
    private volatile Instant currenciesCacheAt;

    public CurrenciesHandler(ExchangeRateClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
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

    private void writeJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(payload);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");

        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void addCorsHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private boolean handleCorsPreflight(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange.getResponseHeaders());

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }

        return false;
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
}
