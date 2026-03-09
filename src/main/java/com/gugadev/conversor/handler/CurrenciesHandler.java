package com.gugadev.conversor.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gugadev.conversor.AppConfig;
import com.gugadev.conversor.ExchangeRateClient;
import com.sun.net.httpserver.HttpExchange;

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
public class CurrenciesHandler extends BaseHandler {

    private static final Duration CURRENCIES_CACHE_TTL =
            Duration.ofHours(AppConfig.getInt(AppConfig.KEY_CURRENCIES_CACHE_TTL_HOURS, AppConfig.DEFAULT_CACHE_TTL_HOURS));

    private final ExchangeRateClient client;
    private volatile List<Map<String, String>> currenciesCache;
    private volatile Instant currenciesCacheAt;

    public CurrenciesHandler(ExchangeRateClient client, ObjectMapper objectMapper) {
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

        try {
            List<Map<String, String>> currencies = getCurrenciesWithCache();
            writeJson(exchange, HTTP_OK, Map.of("currencies", currencies));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            writeJson(exchange, HTTP_INTERNAL_SERVER_ERROR, Map.of("message", "Requisicao interrompida"));
        } catch (IOException ex) {
            writeApiProviderError(exchange, ex);
        }
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
