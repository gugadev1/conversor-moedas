package com.gugadev.conversor;

import com.gugadev.conversor.model.PairConversionResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExchangeRateClientTest {

    @Test
    void shouldConvertWhenApiReturnsHttp200() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{" +
                "\"result\":\"success\"," +
                "\"base_code\":\"USD\"," +
                "\"target_code\":\"BRL\"," +
                "\"conversion_rate\":5.12," +
                "\"conversion_result\":51.2" +
                "}");
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        ExchangeRateClient client = new ExchangeRateClient("test-key", httpClient);

        PairConversionResponse result = client.convert("USD", "BRL", 10.0);

        assertEquals("success", result.result());
        assertEquals("USD", result.baseCode());
        assertEquals("BRL", result.targetCode());
        assertEquals(5.12, result.conversionRate(), 0.0001);
        assertEquals(51.2, result.conversionResult(), 0.0001);
    }

    @Test
    void shouldThrowWhenApiReturnsNon200() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(500);
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        ExchangeRateClient client = new ExchangeRateClient("test-key", httpClient);

        assertThrows(IOException.class, () -> client.convert("USD", "BRL", 10.0));
    }
}
