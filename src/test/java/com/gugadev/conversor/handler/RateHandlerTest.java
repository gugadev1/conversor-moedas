package com.gugadev.conversor.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gugadev.conversor.ExchangeRateClient;
import com.gugadev.conversor.model.PairConversionResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateHandlerTest {

    @Test
    void shouldReturn200ForValidRateQuery() throws Exception {
        ExchangeRateClient client = mock(ExchangeRateClient.class);
        when(client.convert("USD", "BRL", 1.0)).thenReturn(
                new PairConversionResponse("success", "USD", "BRL", 5.2, 5.2, null)
        );

        RateHandler handler = new RateHandler(client, new ObjectMapper());
        FakeHttpExchange exchange = new FakeHttpExchange("GET", "/api/rate?from=USD&to=BRL", null);

        handler.handle(exchange);

        assertEquals(200, exchange.getCapturedStatusCode());
        assertTrue(exchange.getCapturedResponseBody().contains("\"conversionRate\":5.2"));
    }
}
