package com.gugadev.conversor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Classe utilitária para carregar e acessar configurações da aplicação
 * a partir do arquivo {@code application.properties} localizado no classpath.
 *
 * <p>As propriedades são carregadas uma única vez na inicialização da classe
 * e ficam disponíveis de forma estática durante toda a execução.</p>
 *
 * <p>Esta classe não pode ser instanciada.</p>
 *
 * @author gugadev
 */
public final class AppConfig {

    private static final String PROPERTIES_NAME = "application.properties";
    private static final Properties PROPERTIES = loadProperties();

    private AppConfig() {
    }

    /**
     * Carrega as propriedades do arquivo {@code application.properties}
     * presente no classpath.
     *
     * @return um objeto {@link Properties} com as propriedades carregadas,
     *         ou vazio caso o arquivo não seja encontrado
     */
    private static Properties loadProperties() {
        Properties props = new Properties();

        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(PROPERTIES_NAME)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
        }

        return props;
    }

    /**
     * Retorna o valor de uma propriedade como {@link String}.
     *
     * @param key          a chave da propriedade
     * @param defaultValue o valor padrão caso a chave não exista ou esteja em branco
     * @return o valor da propriedade ou {@code defaultValue} se ausente/em branco
     */
    public static String getString(String key, String defaultValue) {
        String value = PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    /**
     * Retorna o valor de uma propriedade como {@code int}.
     *
     * @param key          a chave da propriedade
     * @param defaultValue o valor padrão caso a chave não exista, esteja em branco
     *                     ou não seja um número inteiro válido
     * @return o valor inteiro da propriedade ou {@code defaultValue} em caso de falha
     */
    public static int getInt(String key, int defaultValue) {
        String raw = PROPERTIES.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}