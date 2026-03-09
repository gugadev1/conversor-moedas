package com.gugadev.conversor.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gugadev.conversor.ExchangeRateClient;
import com.gugadev.conversor.model.PairConversionResponse;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler para o endpoint {@code GET /api/rate}.
 */
public class RateHandler extends BaseHandler {

    private static final double UNIT_AMOUNT = 1.0;

    private final ExchangeRateClient client;

    public RateHandler(ExchangeRateClient client, ObjectMapper objectMapper) {
        super(objectMapper);
        this.client = client;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }

        if (!METHOD_GET.equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, HTTP_METHOD_NOT_ALLOWED, Map.of("message", "Metodo nao permitido"));
            return;
        }

        Map<String, String> queryParams = parseQuery(exchange.getRequestURI().getRawQuery());
        String from = normalizeCurrencyCode(queryParams.get("from"));
        String to = normalizeCurrencyCode(queryParams.get("to"));

        if (from == null || to == null) {
            writeJson(exchange, HTTP_BAD_REQUEST, Map.of("message", "Informe from e to na query string."));
            return;
        }

        if (from.equals(to)) {
            writeJson(exchange, HTTP_OK, Map.of(
                    "baseCode", from,
                    "targetCode", to,
                    "conversionRate", UNIT_AMOUNT,
                    "updatedAt", Instant.now().toString()
            ));
            return;
        }

        try {
            PairConversionResponse result = client.convert(from, to, UNIT_AMOUNT);
            if (!ExchangeRateClient.API_RESULT_SUCCESS.equalsIgnoreCase(result.result())) {
                writeJson(exchange, HTTP_BAD_GATEWAY, Map.of("message", "Falha na API de cambio", "errorType", result.errorType()));
                return;
            }

            writeJson(exchange, HTTP_OK, Map.of(
                    "baseCode", result.baseCode(),
                    "targetCode", result.targetCode(),
                    "conversionRate", result.conversionRate(),
                    "updatedAt", Instant.now().toString()
            ));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            writeJson(exchange, HTTP_INTERNAL_SERVER_ERROR, Map.of("message", "Requisicao interrompida"));
        } catch (IOException ex) {
            writeApiProviderError(exchange, ex);
        }
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
}
