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

/**
 * Cliente HTTP para a API do ExchangeRate-API v6.
 *
 * <p>Permite realizar conversão de moedas e consultar a lista de moedas suportadas
 * pela API externa.</p>
 *
 * @author gugadev
 * @see <a href="https://www.exchangerate-api.com/docs/overview">ExchangeRate-API Docs</a>
 */
public class ExchangeRateClient {

    public static final String  API_RESULT_SUCCESS  = "success";
    private static final String  DEFAULT_BASE_URL    = "https://v6.exchangerate-api.com/v6";
    private static final int     HTTP_OK             = 200;
    private static final int     CURRENCY_CODE_INDEX = 0;
    private static final int     CURRENCY_NAME_INDEX = 1;

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Cria uma nova instância do cliente com a chave de API informada.
     *
     * @param apiKey a chave de acesso à ExchangeRate-API; não pode ser nula ou vazia
     * @throws IllegalArgumentException se {@code apiKey} for nula ou vazia
     */
    public ExchangeRateClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey não pode ser nulo ou vazio.");
        }

        this.baseUrl = AppConfig.getString(AppConfig.KEY_API_BASE_URL, DEFAULT_BASE_URL);
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    ExchangeRateClient(String apiKey, HttpClient httpClient) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey não pode ser nulo ou vazio.");
        }
        if (httpClient == null) {
            throw new IllegalArgumentException("httpClient não pode ser nulo.");
        }

        this.baseUrl = AppConfig.getString(AppConfig.KEY_API_BASE_URL, DEFAULT_BASE_URL);
        this.apiKey = apiKey;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Realiza a conversão de um valor entre duas moedas utilizando a API de par de conversão.
     *
     * @param from   código ISO 4217 da moeda de origem (ex.: "USD")
     * @param to     código ISO 4217 da moeda de destino (ex.: "BRL")
     * @param amount valor a ser convertido; deve ser maior que zero
     * @return a resposta da API contendo taxa de conversão e resultado
     * @throws IllegalArgumentException se {@code from} ou {@code to} forem nulos/vazios
     *                                  ou se {@code amount} for menor ou igual a zero
     * @throws IOException              em caso de erro de comunicação com a API
     * @throws InterruptedException     se a requisição for interrompida
     */
    public PairConversionResponse convert(String from, String to, double amount)
            throws IOException, InterruptedException {
        if (from == null || from.isBlank()) {
            throw new IllegalArgumentException("Parâmetro 'from' não pode ser nulo ou vazio.");
        }
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Parâmetro 'to' não pode ser nulo ou vazio.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Parâmetro 'amount' deve ser maior que zero.");
        }
        String encodedFrom = URLEncoder.encode(from, StandardCharsets.UTF_8);
        String encodedTo = URLEncoder.encode(to, StandardCharsets.UTF_8);

        String url = String.format(
                "%s/%s/pair/%s/%s/%.4f",
                baseUrl,
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

        if (response.statusCode() != HTTP_OK) {
            throw new IOException(String.format("Erro HTTP ao chamar API: %d", response.statusCode()));
        }

        return objectMapper.readValue(response.body(), PairConversionResponse.class);
    }

    /**
     * Consulta a lista de moedas suportadas pela ExchangeRate-API.
     *
     * @return lista de {@link CurrencyInfo} ordenada por código da moeda
     * @throws IOException          em caso de erro de comunicação ou resposta com falha da API
     * @throws InterruptedException se a requisição for interrompida
     */
    public List<CurrencyInfo> getSupportedCurrencies() throws IOException, InterruptedException {
        String url = String.format("%s/%s/codes", baseUrl, apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != HTTP_OK) {
            throw new IOException(String.format("Erro HTTP ao chamar API: %d", response.statusCode()));
        }

        SupportedCodesResponse payload = objectMapper.readValue(response.body(), SupportedCodesResponse.class);
        if (!API_RESULT_SUCCESS.equalsIgnoreCase(payload.result())) {
            throw new IOException(String.format("Falha da API ao listar moedas: %s", payload.errorType()));
        }

        List<CurrencyInfo> currencies = new ArrayList<>();
        if (payload.supportedCodes() == null) {
            return currencies;
        }

        for (List<String> entry : payload.supportedCodes()) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }

            String code = entry.get(CURRENCY_CODE_INDEX);
            String name = entry.size() > CURRENCY_NAME_INDEX ? entry.get(CURRENCY_NAME_INDEX) : code;
            if (code == null || code.isBlank()) {
                continue;
            }

            currencies.add(new CurrencyInfo(code.trim().toUpperCase(Locale.US), name == null ? "" : name.trim()));
        }

        currencies.sort(Comparator.comparing(CurrencyInfo::code));
        return currencies;
    }

    /**
     * Representa as informações de uma moeda suportada pela API.
     *
     * @param code código ISO 4217 da moeda (ex.: "BRL")
     * @param name nome descritivo da moeda (ex.: "Brazilian Real")
     */
    public record CurrencyInfo(String code, String name) {
    }
}