package com.gugadev.conversor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SupportedCodesResponse(
        String result,
        @JsonProperty("supported_codes") List<List<String>> supportedCodes,
        @JsonProperty("error-type") String errorType
) {
}
