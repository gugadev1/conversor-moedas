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

    private final Path frontendDir;

    public StaticFileHandler(Path frontendDir) {
        this.frontendDir = frontendDir;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String rawPath = exchange.getRequestURI().getPath();
        String relativePath = "/".equals(rawPath) ? "index.html" : rawPath.substring(1);

        Path targetFile = frontendDir.resolve(relativePath).normalize();
        if (!targetFile.startsWith(frontendDir) || !Files.exists(targetFile) || Files.isDirectory(targetFile)) {
            writeText(exchange, 404, "Arquivo nao encontrado", "text/plain; charset=utf-8");
            return;
        }

        byte[] content = Files.readAllBytes(targetFile);
        String contentType = Files.probeContentType(targetFile);
        if (contentType == null) {
            contentType = inferContentType(targetFile.getFileName().toString());
        }

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, content.length);
        exchange.getResponseBody().write(content);
        exchange.close();
    }

    private void writeText(HttpExchange exchange, int statusCode, String message, String contentType) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private String inferContentType(String fileName) {
        if (fileName.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (fileName.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (fileName.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        return "application/octet-stream";
    }
}
