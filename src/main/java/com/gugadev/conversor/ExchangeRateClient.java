package com.gugadev.conversor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gugadev.conversor.model.PairConversionResponse;
import com.gugadev.conversor.model.SupportedCodesResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ExchangeRateClient {
    private static final String BASE_URL = "https://v6.exchangerate-api.com/v6";

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ExchangeRateClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public PairConversionResponse convert(String from, String to, double amount)
            throws IOException, InterruptedException {
        String encodedFrom = URLEncoder.encode(from, StandardCharsets.UTF_8);
        String encodedTo = URLEncoder.encode(to, StandardCharsets.UTF_8);

        String url = String.format(
                "%s/%s/pair/%s/%s/%.4f",
                BASE_URL,
                apiKey,
                encodedFrom,
                encodedTo,
                amount
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Erro HTTP ao chamar API: " + response.statusCode());
        }

        return objectMapper.readValue(response.body(), PairConversionResponse.class);
    }

    public List<CurrencyInfo> getSupportedCurrencies() throws IOException, InterruptedException {
        String url = String.format("%s/%s/codes", BASE_URL, apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Erro HTTP ao chamar API: " + response.statusCode());
        }

        SupportedCodesResponse payload = objectMapper.readValue(response.body(), SupportedCodesResponse.class);
        if (!"success".equalsIgnoreCase(payload.result())) {
            throw new IOException("Falha da API ao listar moedas: " + payload.errorType());
        }

        List<CurrencyInfo> currencies = new ArrayList<>();
        if (payload.supportedCodes() == null) {
            return currencies;
        }

        for (List<String> entry : payload.supportedCodes()) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }

            String code = entry.get(0);
            String name = entry.size() > 1 ? entry.get(1) : code;
            if (code == null || code.isBlank()) {
                continue;
            }

            currencies.add(new CurrencyInfo(code.trim().toUpperCase(Locale.US), name == null ? "" : name.trim()));
        }

        currencies.sort(Comparator.comparing(CurrencyInfo::code));
        return currencies;
    }

    public record CurrencyInfo(String code, String name) {
    }
}
