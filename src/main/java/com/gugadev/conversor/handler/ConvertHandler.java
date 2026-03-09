package com.gugadev.conversor.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gugadev.conversor.ExchangeRateClient;
import com.gugadev.conversor.model.PairConversionResponse;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Handler para o endpoint {@code POST /api/convert}.
 */
public class ConvertHandler extends BaseHandler {

    private final ExchangeRateClient client;

    public ConvertHandler(ExchangeRateClient client, ObjectMapper objectMapper) {
        super(objectMapper);
        this.client = client;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) {
            return;
        }

        if (!METHOD_POST.equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, HTTP_METHOD_NOT_ALLOWED, Map.of("message", "Metodo nao permitido"));
            return;
        }

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            ConversionRequest request = objectMapper.readValue(body, ConversionRequest.class);

            String from = normalizeCurrencyCode(request.from());
            String to = normalizeCurrencyCode(request.to());
            double amount = request.amount();

            if (from == null || to == null || amount <= 0) {
                writeJson(exchange, HTTP_BAD_REQUEST, Map.of("message", "Dados invalidos. Informe moedas e valor maior que zero."));
                return;
            }

            PairConversionResponse result = client.convert(from, to, amount);
            if (!ExchangeRateClient.API_RESULT_SUCCESS.equalsIgnoreCase(result.result())) {
                writeJson(exchange, HTTP_BAD_GATEWAY, Map.of("message", "Falha na API de cambio", "errorType", result.errorType()));
                return;
            }

            writeJson(exchange, HTTP_OK, Map.of(
                    "baseCode", result.baseCode(),
                    "targetCode", result.targetCode(),
                    "conversionRate", result.conversionRate(),
                    "conversionResult", result.conversionResult(),
                    "amount", amount
            ));
        } catch (JsonProcessingException ex) {
            writeJson(exchange, HTTP_BAD_REQUEST, Map.of("message", "JSON invalido no corpo da requisicao"));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            writeJson(exchange, HTTP_INTERNAL_SERVER_ERROR, Map.of("message", "Requisicao interrompida"));
        } catch (IOException ex) {
            writeApiProviderError(exchange, ex);
        }
    }

    private record ConversionRequest(String from, String to, double amount) {
    }
}
