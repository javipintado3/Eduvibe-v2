package com.eduvibe.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP del cliente que hace la petición.
 *
 * Detrás de un proxy (nginx) la dirección de la conexión es la del proxy, no la
 * de la persona, y el límite por IP penalizaría a todo el mundo a la vez. El
 * proxy deja la real en X-Forwarded-For, pero esa cabecera solo es fiable si la
 * pone un proxy nuestro: si la API es accesible directamente, cualquiera puede
 * enviarla con el valor que quiera para esquivar el límite. Por eso solo se lee
 * cuando se declara explícitamente que hay un proxy de confianza.
 */
public final class ClientIp {

    private static final String CABECERA = "X-Forwarded-For";

    private ClientIp() {
    }

    public static String de(HttpServletRequest peticion, boolean proxyDeConfianza) {
        if (proxyDeConfianza) {
            String reenviada = peticion.getHeader(CABECERA);
            if (reenviada != null && !reenviada.isBlank()) {
                // El proxy añade al final la dirección que él ha visto; lo que
                // haya antes lo ha podido escribir el propio cliente
                String[] direcciones = reenviada.split(",");
                return direcciones[direcciones.length - 1].trim();
            }
        }
        return peticion.getRemoteAddr();
    }
}
