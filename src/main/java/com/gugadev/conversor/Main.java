package com.gugadev.conversor;

import com.gugadev.conversor.model.PairConversionResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Locale.setDefault(Locale.US);

        String apiKey = System.getenv("EXCHANGE_RATE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("Defina a variavel EXCHANGE_RATE_API_KEY antes de executar.");
            System.out.println("Exemplo: export EXCHANGE_RATE_API_KEY=seu_token");
            return;
        }

        if (args.length > 0 && "--cli".equalsIgnoreCase(args[0])) {
            runCli(apiKey);
            return;
        }

        runWeb(apiKey);
    }

    private static void runWeb(String apiKey) {
        int port = resolvePort();

        try {
            WebServer webServer = new WebServer(apiKey, port);
            webServer.start();
        } catch (IOException e) {
            System.out.printf("Falha ao iniciar servidor web: %s%n", e.getMessage());
        }
    }

    private static int resolvePort() {
        String configuredPort = System.getenv("PORT");
        if (configuredPort == null || configuredPort.isBlank()) {
            return 8080;
        }

        try {
            return Integer.parseInt(configuredPort);
        } catch (NumberFormatException ex) {
            System.out.println("Valor invalido em PORT. Usando porta 8080.");
            return 8080;
        }
    }

    private static void runCli(String apiKey) {
        try (Scanner scanner = new Scanner(System.in)) {
            ExchangeRateClient client = new ExchangeRateClient(apiKey);

            System.out.println("=== Conversor de Moedas (Java + ExchangeRate API) ===");
            System.out.println("Digite SAIR na moeda de origem para encerrar.\n");

            while (true) {
                System.out.println("Moeda de origem:");
                String from = scanner.nextLine().trim().toUpperCase();
                if ("SAIR".equals(from)) {
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
                    System.out.println("Valor invalido. Digite um numero.\n");
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
                    System.out.printf("Erro de comunicacao com a API: %s%n%n", e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Execucao interrompida.");
                    return;
                }
            }

            System.out.println("Encerrando conversor.");
        }
    }
}
