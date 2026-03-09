package com.gugadev.conversor;

import com.gugadev.conversor.model.PairConversionResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.Scanner;

/**
 * Ponto de entrada da aplicação Conversor de Moedas.
 *
 * <p>Suporta dois modos de execução:</p>
 * <ul>
 *   <li><b>Web (padrão)</b> — inicia um servidor HTTP para servir a interface frontend e
 *       os endpoints REST de conversão.</li>
 *   <li><b>CLI</b> — modo interativo por linha de comando, ativado com o argumento
 *       {@code --cli}.</li>
 * </ul>
 *
 * <p>Requer a variável de ambiente {@code EXCHANGE_RATE_API_KEY} definida com uma
 * chave válida da ExchangeRate-API.</p>
 *
 * @author gugadev
 */
public class Main {

    private static final String ENV_API_KEY      = "EXCHANGE_RATE_API_KEY";
    private static final String ENV_PORT         = "PORT";
    private static final String ARG_CLI_FLAG     = "--cli";
    private static final String CLI_EXIT_COMMAND = "SAIR";

    /** Esta classe não pode ser instanciada. */
    private Main() {
    }
    /**
     * Método principal da aplicação.
     *
     * @param args argumentos de linha de comando; utilize {@code --cli} para modo interativo
     */
    public static void main(String[] args) {
        Locale.setDefault(Locale.US);

        String apiKey = System.getenv(ENV_API_KEY);
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("Defina a variável EXCHANGE_RATE_API_KEY antes de executar.");
            System.out.println("Exemplo: export EXCHANGE_RATE_API_KEY=seu_token");
            return;
        }

        if (args.length > 0 && ARG_CLI_FLAG.equalsIgnoreCase(args[0])) {
            runCli(apiKey);
            return;
        }

        runWeb(apiKey);
    }

    /**
     * Inicia o servidor web HTTP na porta configurada.
     *
     * <p>A porta é resolvida a partir da variável de ambiente {@code PORT} ou,
     * na sua ausência, do valor definido em {@code application.properties}
     * (chave {@code server.port.default}), com fallback para {@code 8080}.</p>
     *
     * @param apiKey chave de acesso à ExchangeRate-API
     */
    private static void runWeb(String apiKey) {
        int port = resolvePort();

        try {
            WebServer webServer = new WebServer(apiKey, port);
            webServer.start();
        } catch (IOException e) {
            System.out.printf("Falha ao iniciar servidor web: %s%n", e.getMessage());
        }
    }

    /**
     * Resolve a porta HTTP a ser utilizada pelo servidor.
     *
     * <p>Prioridade: variável de ambiente {@code PORT} &gt;
     * propriedade {@code server.port.default} &gt; {@code 8080}.</p>
     *
     * @return a porta resolvida
     */
    private static int resolvePort() {
        String configuredPort = System.getenv(ENV_PORT);
        int defaultPort = AppConfig.getInt(AppConfig.KEY_SERVER_PORT_DEFAULT, AppConfig.DEFAULT_SERVER_PORT);
        if (configuredPort == null || configuredPort.isBlank()) {
            return defaultPort;
        }

        try {
            return Integer.parseInt(configuredPort);
        } catch (NumberFormatException ex) {
            System.out.printf("Valor inválido em PORT. Usando porta %d.%n", defaultPort);
            return defaultPort;
        }
    }

    /**
     * Executa o conversor no modo interativo de linha de comando (CLI).
     *
     * <p>O usuário informa a moeda de origem, a moeda de destino e o valor
     * desejado. O resultado é exibido no console. Digite {@code SAIR} na
     * moeda de origem para encerrar.</p>
     *
     * @param apiKey chave de acesso à ExchangeRate-API
     */
    private static void runCli(String apiKey) {
        try (Scanner scanner = new Scanner(System.in)) {
            ExchangeRateClient client = new ExchangeRateClient(apiKey);

            System.out.println("=== Conversor de Moedas (Java + ExchangeRate API) ===");
            System.out.println("Digite SAIR na moeda de origem para encerrar.\n");

            while (true) {
                System.out.println("Moeda de origem:");
                String from = scanner.nextLine().trim().toUpperCase();
                if (CLI_EXIT_COMMAND.equals(from)) {
                    break;
                }

                System.out.println("Moeda de destino:");
                String to = scanner.nextLine().trim().toUpperCase();

                System.out.println("Valor:");
                String amountInput = scanner.nextLine().trim();

                double amount;
                try {
                    amount = Double.parseDouble(amountInput);
                    if (amount <= 0) {
                        System.out.println("Informe um valor maior que zero.\n");
                        continue;
                    }
                } catch (NumberFormatException ex) {
                    System.out.println("Valor inválido. Digite um número.\n");
                    continue;
                }

                try {
                    PairConversionResponse result = client.convert(from, to, amount);
                    if (!"success".equalsIgnoreCase(result.result())) {
                        System.out.printf("Falha da API. Motivo: %s%n%n", result.errorType());
                        continue;
                    }

                    System.out.printf("Taxa %s -> %s: %.6f%n", result.baseCode(), result.targetCode(), result.conversionRate());
                    System.out.printf("Resultado: %.2f %s = %.2f %s%n%n",
                            amount,
                            result.baseCode(),
                            result.conversionResult(),
                            result.targetCode());
                } catch (IOException e) {
                    System.out.printf("Erro de comunicação com a API: %s%n%n", e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Execução interrompida.");
                    return;
                }
            }

            System.out.println("Encerrando conversor.");
        }
    }
}