package com.eduvibe.repository.spec;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.eduvibe.model.Enrollment;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.UserRole;
import com.eduvibe.model.enums.UserStatus;

import jakarta.persistence.criteria.Subquery;

/**
 * Filtros componibles para el listado de usuarios.
 *
 * Se usan Specifications en lugar de una consulta JPQL con condiciones del
 * estilo ":filtro IS NULL OR campo = :filtro" porque esa forma obliga a enviar
 * un parámetro nulo a la base de datos, y PostgreSQL no puede deducir de qué
 * tipo es: llega a interpretarlo como bytea y la consulta revienta con
 * "function lower(bytea) does not exist".
 *
 * Con Specifications el filtro que no se usa sencillamente no se añade, así que
 * la consulta que se ejecuta solo contiene las condiciones que de verdad
 * aplican. Además cada criterio queda aislado y se puede reutilizar y probar
 * por separado.
 *
 * Un método devuelve null cuando su filtro no aplica: al componer con and(),
 * Spring Data ignora los null.
 */
public final class UserSpecifications {

    private UserSpecifications() {
        // Clase de utilidades: no se instancia
    }

    /** Acota a una organización. Es el único filtro que nunca es opcional. */
    public static Specification<User> deOrganizacion(UUID organizationId) {
        return (raiz, consulta, cb) -> cb.equal(raiz.get("organization").get("id"), organizationId);
    }

    /** Deja fuera a quien la administración ha eliminado: ya no es nadie del centro. */
    public static Specification<User> noEliminados() {
        return (raiz, consulta, cb) -> cb.isNull(raiz.get("deletedAt"));
    }

    public static Specification<User> conRol(UserRole rol) {
        if (rol == null) {
            return null;
        }
        return (raiz, consulta, cb) -> cb.equal(raiz.get("role"), rol);
    }

    public static Specification<User> conEstado(UserStatus estado) {
        if (estado == null) {
            return null;
        }
        return (raiz, consulta, cb) -> cb.equal(raiz.get("status"), estado);
    }

    /**
     * Excluye a quien ya está matriculado en esa clase.
     *
     * Vive aquí y no se filtra en el cliente para que la paginación
     * (totalElementos, totalPaginas) que devuelve el Pageable sea correcta:
     * si se filtrase después de traer la página, una página podría llegar con
     * menos filas de las que pide `size`, o el conteo total no cuadraría con
     * lo que de verdad se puede añadir.
     */
    public static Specification<User> noMatriculadoEn(UUID classId) {
        if (classId == null) {
            return null;
        }
        return (raiz, consulta, cb) -> {
            Subquery<UUID> matriculados = consulta.subquery(UUID.class);
            var enrollment = matriculados.from(Enrollment.class);
            matriculados.select(enrollment.get("user").get("id"))
                    .where(cb.equal(enrollment.get("schoolClass").get("id"), classId));

            return cb.not(raiz.get("id").in(matriculados));
        };
    }

    /** Busca el texto en el nombre o en el email, sin distinguir mayúsculas. */
    public static Specification<User> queContenga(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String patron = "%" + texto.trim().toLowerCase() + "%";

        return (raiz, consulta, cb) -> cb.or(
                cb.like(cb.lower(raiz.get("name")), patron),
                cb.like(cb.lower(raiz.get("email")), patron));
    }
}
