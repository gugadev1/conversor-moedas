package com.gugadev.conversor.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gugadev.conversor.ExchangeRateClient;
import com.gugadev.conversor.model.PairConversionResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConvertHandlerTest {

    @Test
    void shouldReturn200ForValidRequest() throws Exception {
        ExchangeRateClient client = mock(ExchangeRateClient.class);
        when(client.convert("USD", "BRL", 10.0)).thenReturn(
                new PairConversionResponse("success", "USD", "BRL", 5.0, 50.0, null)
        );

        ConvertHandler handler = new ConvertHandler(client, new ObjectMapper());
        FakeHttpExchange exchange = new FakeHttpExchange(
                "POST",
                "/api/convert",
                "{\"from\":\"USD\",\"to\":\"BRL\",\"amount\":10}"
        );

        handler.handle(exchange);

        assertEquals(200, exchange.getCapturedStatusCode());
        assertTrue(exchange.getCapturedResponseBody().contains("\"conversionResult\":50.0"));
    }
}
