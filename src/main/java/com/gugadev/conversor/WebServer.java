package com.gugadev.conversor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gugadev.conversor.handler.ConvertHandler;
import com.gugadev.conversor.handler.CurrenciesHandler;
import com.gugadev.conversor.handler.RateHandler;
import com.gugadev.conversor.handler.StaticFileHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP embutido que expõe endpoints REST para conversão de moedas
 * e serve os arquivos estáticos da interface frontend.
 *
 * <h2>Endpoints disponíveis:</h2>
 * <ul>
 *   <li>{@code POST /api/convert} — converte um valor entre duas moedas</li>
 *   <li>{@code GET  /api/rate}    — consulta a taxa de câmbio entre duas moedas</li>
 *   <li>{@code GET  /api/currencies} — lista as moedas suportadas (com cache)</li>
 *   <li>{@code GET  /}            — serve arquivos estáticos do diretório {@code frontend/}</li>
 * </ul>
 *
 * @author gugadev
 */
public class WebServer {

    private static final Path FRONTEND_DIR = Path.of("frontend").toAbsolutePath().normalize();
    private static final int THREAD_POOL_SIZE = 8;
    private static final int HTTP_BACKLOG = 0;

    private final ExchangeRateClient client;
    private final ObjectMapper objectMapper;
    private final int port;

    /**
     * Cria uma nova instância do servidor web.
     *
     * @param apiKey chave de acesso à ExchangeRate-API; não pode ser nula ou vazia
     * @param port   porta TCP para escutar conexões (1–65535)
     * @throws IllegalArgumentException se {@code apiKey} for nula/vazia ou {@code port} estiver fora do intervalo
     */
    public WebServer(String apiKey, int port) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey não pode ser nulo ou vazio.");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Porta deve estar no intervalo 1-65535.");
        }

        this.client = new ExchangeRateClient(apiKey);
        this.objectMapper = new ObjectMapper();
        this.port = port;
    }

    /**
     * Inicia o servidor HTTP e registra os handlers de cada endpoint.
     *
     * @throws IOException se não for possível criar o socket do servidor
     */
    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), HTTP_BACKLOG);
        server.createContext("/api/convert", new ConvertHandler(client, objectMapper));
        server.createContext("/api/rate", new RateHandler(client, objectMapper));
        server.createContext("/api/currencies", new CurrenciesHandler(client, objectMapper));
        server.createContext("/", new StaticFileHandler(FRONTEND_DIR));
        server.setExecutor(Executors.newFixedThreadPool(THREAD_POOL_SIZE));
        server.start();

        System.out.printf("Servidor iniciado em http://localhost:%d%n", port);
    }
}