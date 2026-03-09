package com.gugadev.conversor.handler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticFileHandlerTest {

    @Test
    void shouldServeIndexHtmlWhenPathIsRoot() throws Exception {
        Path dir = Files.createTempDirectory("static-handler-test");
        Files.writeString(dir.resolve("index.html"), "<html><body>ok</body></html>");

        StaticFileHandler handler = new StaticFileHandler(dir);
        FakeHttpExchange exchange = new FakeHttpExchange("GET", "/", null);

        handler.handle(exchange);

        assertEquals(200, exchange.getCapturedStatusCode());
        assertTrue(exchange.getCapturedResponseBody().contains("ok"));
    }
}
