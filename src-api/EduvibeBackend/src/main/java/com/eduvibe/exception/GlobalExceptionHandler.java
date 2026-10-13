package com.eduvibe.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.eduvibe.dto.common.ErrorResponse;

/**
 * Convierte las excepciones en respuestas con la misma forma.
 *
 * Centralizarlo aquí evita que cada controlador invente su propio formato de
 * error, y sobre todo evita el problema de la versión anterior: una única
 * excepción para todo, anotada con un código HTTP fijo, que hacía que un fallo
 * de credenciales se devolviese como 404.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Errores propios de la aplicación: cada uno ya sabe su código. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, WebRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request);
    }

    /** Credenciales incorrectas o cuenta no habilitada. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleCredentials(BadCredentialsException ex, WebRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    /**
     * Autenticado, pero sin permiso. Las denegaciones que ocurren en la cadena
     * de filtros las atiende RestAccessDeniedHandler; aquí llegan las que
     * lanza la seguridad a nivel de método.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccess(AccessDeniedException ex, WebRequest request) {
        return build(HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta operación", request);
    }

    /** Fallos de validación de los DTO: se devuelve el detalle por campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(ErrorResponse.ofValidation(path(request), campos));
    }

    /**
     * Cuerpo de la petición ilegible: JSON mal formado, mal codificado, o un
     * campo con un tipo que no encaja. Es culpa de quien llama, así que es un
     * 400; sin este manejador caía en la red de seguridad y se devolvía un 500,
     * que haría buscar el problema en el sitio equivocado.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleBodyUnreadable(HttpMessageNotReadableException ex,
                                                               WebRequest request) {
        LOG.debug("Cuerpo de petición ilegible en {}: {}", path(request), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                "El cuerpo de la petición no se ha podido interpretar. Revisa que sea JSON válido en UTF-8.",
                request);
    }

    /** Ruta inexistente: 404 y no 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNonexistentPath(NoResourceFoundException ex,
                                                                WebRequest request) {
        return build(HttpStatus.NOT_FOUND, "No existe el recurso solicitado", request);
    }

    /**
     * Un parámetro de ruta con formato imposible, por ejemplo un identificador
     * que no es un UUID. También es culpa de quien llama.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleIncorrectType(MethodArgumentTypeMismatchException ex,
                                                               WebRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "El valor de '" + ex.getName() + "' no tiene el formato esperado", request);
    }

    /**
     * Red de seguridad. Se registra la traza completa en el log pero al cliente
     * solo le llega un mensaje genérico: los detalles internos de un fallo no
     * previsto no deben salir de la aplicación.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, WebRequest request) {
        LOG.error("Error no controlado en {}", path(request), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ha ocurrido un error inesperado", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, WebRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), status.getReasonPhrase(), message, path(request)));
    }

    private String path(WebRequest request) {
        return request.getDescription(false).replaceFirst("^uri=", "");
    }
}
