package com.gugadev.conversor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PairConversionResponse(
        String result,
        @JsonProperty("base_code") String baseCode,
        @JsonProperty("target_code") String targetCode,
        @JsonProperty("conversion_rate") double conversionRate,
        @JsonProperty("conversion_result") double conversionResult,
        @JsonProperty("error-type") String errorType
) {
}
