package com.gugadev.conversor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Representa a resposta da API ExchangeRate para conversão de par de moedas.
 *
 * <p>Mapeia os campos JSON retornados pelo endpoint
 * {@code /pair/{from}/{to}/{amount}}.</p>
 *
 * @param result           status da requisição ({@code "success"} ou erro)
 * @param baseCode         código ISO 4217 da moeda de origem
 * @param targetCode       código ISO 4217 da moeda de destino
 * @param conversionRate   taxa de conversão entre as moedas
 * @param conversionResult valor convertido na moeda de destino
 * @param errorType        tipo de erro retornado pela API, caso haja falha
 */
public record PairConversionResponse(
        String result,
        @JsonProperty("base_code") String baseCode,
        @JsonProperty("target_code") String targetCode,
        @JsonProperty("conversion_rate") double conversionRate,
        @JsonProperty("conversion_result") double conversionResult,
        @JsonProperty("error-type") String errorType
) {
}