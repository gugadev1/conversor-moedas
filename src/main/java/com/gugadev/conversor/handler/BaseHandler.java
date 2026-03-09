package com.gugadev.conversor.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * Classe base para todos os handlers HTTP da aplicação.
 *
 * <p>Centraliza lógica comum: escrita de respostas JSON,
 * tratamento de preflight CORS e normalização de códigos de moeda.</p>
 */
public abstract class BaseHandler implements HttpHandler {

    protected static final int HTTP_OK                    = 200;
    protected static final int HTTP_NO_CONTENT            = 204;
    protected static final int HTTP_BAD_REQUEST           = 400;
    protected static final int HTTP_METHOD_NOT_ALLOWED    = 405;
    protected static final int HTTP_INTERNAL_SERVER_ERROR = 500;
    protected static final int HTTP_BAD_GATEWAY           = 502;

    protected static final String METHOD_GET     = "GET";
    protected static final String METHOD_POST    = "POST";
    private static final String   METHOD_OPTIONS = "OPTIONS";

    private static final String CORS_HEADER_ALLOW_ORIGIN  = "Access-Control-Allow-Origin";
    private static final String CORS_HEADER_ALLOW_METHODS = "Access-Control-Allow-Methods";
    private static final String CORS_HEADER_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    private static final String CORS_ORIGIN_ALL           = "*";
    private static final String CORS_ALLOWED_METHODS      = "GET, POST, OPTIONS";
    private static final String CORS_ALLOWED_HEADERS      = "Content-Type";

    private static final String   HEADER_CONTENT_TYPE = "Content-Type";
    protected static final String CONTENT_TYPE_JSON   = "application/json; charset=utf-8";

    protected final ObjectMapper objectMapper;

    protected BaseHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializa {@code payload} como JSON e envia a resposta HTTP.
     */
    protected void writeJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(payload);
        Headers headers = exchange.getResponseHeaders();
        headers.set(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    /**
     * Envia uma resposta 502 indicando falha ao consultar a API externa.
     */
    protected void writeApiProviderError(HttpExchange exchange, IOException ex) throws IOException {
        writeJson(exchange, HTTP_BAD_GATEWAY,
                Map.of("message", "Erro ao consultar API externa", "detail", ex.getMessage()));
    }

    /**
     * Adiciona os cabeçalhos CORS à resposta.
     */
    protected void addCorsHeaders(Headers headers) {
        headers.set(CORS_HEADER_ALLOW_ORIGIN,  CORS_ORIGIN_ALL);
        headers.set(CORS_HEADER_ALLOW_METHODS, CORS_ALLOWED_METHODS);
        headers.set(CORS_HEADER_ALLOW_HEADERS, CORS_ALLOWED_HEADERS);
    }

    /**
     * Trata requisições OPTIONS (preflight CORS).
     *
     * @return {@code true} se a requisição era um preflight e já foi respondida
     */
    protected boolean handleCorsPreflight(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange.getResponseHeaders());
        if (METHOD_OPTIONS.equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(HTTP_NO_CONTENT, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    /**
     * Normaliza um código de moeda: remove espaços e converte para maiúsculas.
     *
     * @return código normalizado, ou {@code null} se {@code code} for nulo/vazio
     */
    protected String normalizeCurrencyCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase(Locale.US);
    }
}
