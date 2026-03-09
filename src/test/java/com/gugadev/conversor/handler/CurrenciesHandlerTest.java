package com.gugadev.conversor.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gugadev.conversor.ExchangeRateClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrenciesHandlerTest {

    @Test
    void shouldReturn200WithCurrenciesList() throws Exception {
        ExchangeRateClient client = mock(ExchangeRateClient.class);
        when(client.getSupportedCurrencies()).thenReturn(List.of(
                new ExchangeRateClient.CurrencyInfo("USD", "United States Dollar"),
                new ExchangeRateClient.CurrencyInfo("BRL", "Brazilian Real")
        ));

        CurrenciesHandler handler = new CurrenciesHandler(client, new ObjectMapper());
        FakeHttpExchange exchange = new FakeHttpExchange("GET", "/api/currencies", null);

        handler.handle(exchange);

        assertEquals(200, exchange.getCapturedStatusCode());
        assertTrue(exchange.getCapturedResponseBody().contains("\"currencies\""));
        assertTrue(exchange.getCapturedResponseBody().contains("\"USD\""));
    }
}
