package com.gugadev.conversor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Representa a resposta da API ExchangeRate para a listagem de moedas suportadas.
 *
 * <p>Mapeia os campos JSON retornados pelo endpoint {@code /codes}.</p>
 *
 * @param result         status da requisição ({@code "success"} ou erro)
 * @param supportedCodes lista de pares {@code [código, nome]} das moedas suportadas
 * @param errorType      tipo de erro retornado pela API, caso haja falha
 */
public record SupportedCodesResponse(
        String result,
        @JsonProperty("supported_codes") List<List<String>> supportedCodes,
        @JsonProperty("error-type") String errorType
) {
}