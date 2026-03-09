package com.gugadev.conversor.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gugadev.conversor.ExchangeRateClient;
import com.gugadev.conversor.model.PairConversionResponse;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Handler para o endpoint {@code POST /api/convert}.
 */
public class ConvertHandler implements HttpHandler {

    private final ExchangeRateClient client;
    private final ObjectMapper objectMapper;

    public ConvertHandler(ExchangeRateClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
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

    private void writeApiProviderError(HttpExchange exchange, IOException ex) throws IOException {
        writeJson(exchange, 502, Map.of("message", "Erro ao consultar API externa", "detail", ex.getMessage()));
    }

    private record ConversionRequest(String from, String to, double amount) {
    }
}
