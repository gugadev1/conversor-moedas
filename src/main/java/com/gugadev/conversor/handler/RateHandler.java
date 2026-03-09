package com.gugadev.conversor.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gugadev.conversor.ExchangeRateClient;
import com.gugadev.conversor.model.PairConversionResponse;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Handler para o endpoint {@code GET /api/rate}.
 */
public class RateHandler implements HttpHandler {

    private final ExchangeRateClient client;
    private final ObjectMapper objectMapper;

    public RateHandler(ExchangeRateClient client, ObjectMapper objectMapper) {
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

    private void writeJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(payload);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");

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

    private boolean handleCorsPreflight(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange.getResponseHeaders());

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }

        return false;
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
}
