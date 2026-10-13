package com.eduvibe.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Lectura y escritura de CSV sin depender de una librería externa.
 *
 * Sigue RFC 4180 en lo justo que necesita el proyecto: campos entre comillas
 * cuando llevan coma, comilla o salto de línea, con las comillas internas
 * duplicadas. No soporta un campo con un salto de línea real dentro (poco
 * probable en nombre, email o rol); si hiciera falta ese caso, es el momento
 * de traer una librería de verdad en lugar de complicar esto.
 */
public final class Csv {

    private Csv() {
    }

    public static String fila(String... campos) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escape(campos[i]));
        }
        return line.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuotes ? "\"" + escaped + "\"" : escaped;
    }

    /** Cada fila ya separada en campos. La primera fila es la cabecera, si la hay. */
    public static List<List<String>> read(Reader input) throws IOException {
        List<List<String>> filas = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(input)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                filas.add(parseLine(line));
            }
        }
        return filas;
    }

    private static List<String> parseLine(String line) {
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean betweenQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (betweenQuotes) {
                if (c == '"') {
                    boolean escapedQuote = i + 1 < line.length() && line.charAt(i + 1) == '"';
                    if (escapedQuote) {
                        actual.append('"');
                        i++;
                    } else {
                        betweenQuotes = false;
                    }
                } else {
                    actual.append(c);
                }
            } else if (c == '"') {
                betweenQuotes = true;
            } else if (c == ',') {
                campos.add(actual.toString().trim());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        campos.add(actual.toString().trim());
        return campos;
    }
}
