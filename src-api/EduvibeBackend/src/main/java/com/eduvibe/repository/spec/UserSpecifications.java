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
    public static Specification<User> ofOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organization").get("id"), organizationId);
    }

    /** Deja fuera a quien la administración ha eliminado: ya no es nadie del centro. */
    public static Specification<User> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<User> withRole(UserRole role) {
        if (role == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    public static Specification<User> withStatus(UserStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
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
    public static Specification<User> notEnrolledIn(UUID classId) {
        if (classId == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<UUID> enrolled = query.subquery(UUID.class);
            var enrollment = enrolled.from(Enrollment.class);
            enrolled.select(enrollment.get("user").get("id"))
                    .where(cb.equal(enrollment.get("schoolClass").get("id"), classId));

            return cb.not(root.get("id").in(enrolled));
        };
    }

    /** Busca el texto en el nombre o en el email, sin distinguir mayúsculas. */
    public static Specification<User> thatContains(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String pattern = "%" + texto.trim().toLowerCase() + "%";

        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("email")), pattern));
    }
}
