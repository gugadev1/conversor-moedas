package com.gugadev.conversor.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handler para servir arquivos estaticos do diretorio frontend.
 */
public class StaticFileHandler implements HttpHandler {

    private static final int    HTTP_OK                 = 200;
    private static final int    HTTP_NOT_FOUND          = 404;
    private static final int    HTTP_METHOD_NOT_ALLOWED = 405;

    private static final String DEFAULT_FILE        = "index.html";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_PLAIN  = "text/plain; charset=utf-8";
    private static final String CONTENT_TYPE_HTML   = "text/html; charset=utf-8";
    private static final String CONTENT_TYPE_CSS    = "text/css; charset=utf-8";
    private static final String CONTENT_TYPE_JS     = "application/javascript; charset=utf-8";
    private static final String CONTENT_TYPE_BINARY = "application/octet-stream";

    private final Path frontendDir;

    public StaticFileHandler(Path frontendDir) {
        this.frontendDir = frontendDir;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(HTTP_METHOD_NOT_ALLOWED, -1);
            exchange.close();
            return;
        }

        String rawPath = exchange.getRequestURI().getPath();
        String relativePath = "/".equals(rawPath) ? DEFAULT_FILE : rawPath.substring(1);

        Path targetFile = frontendDir.resolve(relativePath).normalize();
        if (!targetFile.startsWith(frontendDir) || !Files.exists(targetFile) || Files.isDirectory(targetFile)) {
            writeText(exchange, HTTP_NOT_FOUND, "Arquivo nao encontrado", CONTENT_TYPE_PLAIN);
            return;
        }

        byte[] content = Files.readAllBytes(targetFile);
        String contentType = Files.probeContentType(targetFile);
        if (contentType == null) {
            contentType = inferContentType(targetFile.getFileName().toString());
        }

        exchange.getResponseHeaders().set(HEADER_CONTENT_TYPE, contentType);
        exchange.sendResponseHeaders(HTTP_OK, content.length);
        exchange.getResponseBody().write(content);
        exchange.close();
    }

    private void writeText(HttpExchange exchange, int statusCode, String message, String contentType) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(HEADER_CONTENT_TYPE, contentType);
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private String inferContentType(String fileName) {
        if (fileName.endsWith(".html")) {
            return CONTENT_TYPE_HTML;
        }
        if (fileName.endsWith(".css")) {
            return CONTENT_TYPE_CSS;
        }
        if (fileName.endsWith(".js")) {
            return CONTENT_TYPE_JS;
        }
        return CONTENT_TYPE_BINARY;
    }
}
