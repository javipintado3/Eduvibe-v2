package com.eduvibe.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eduvibe.model.Enrollment;
import com.eduvibe.model.enums.EnrollmentRole;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    Optional<Enrollment> findBySchoolClassIdAndUserId(UUID classId, UUID userId);

    boolean existsBySchoolClassIdAndUserId(UUID classId, UUID userId);

    boolean existsBySchoolClassIdAndUserIdAndRoleInClass(UUID classId, UUID userId, EnrollmentRole role);

    /**
     * Personas de una clase. Se trae el usuario en la misma consulta porque
     * siempre se va a necesitar su nombre y su email: sin esto, pintar la lista
     * dispararía una consulta por fila.
     */
    @EntityGraph(attributePaths = "user")
    List<Enrollment> findBySchoolClassIdOrderByRoleInClassAscUserNameAsc(UUID classId);

    @EntityGraph(attributePaths = "user")
    List<Enrollment> findBySchoolClassIdAndRoleInClassOrderByUserNameAsc(UUID classId, EnrollmentRole role);

    long countBySchoolClassIdAndRoleInClass(UUID classId, EnrollmentRole role);

    /** Para el resumen de perfil: en cuántas clases participa, como profesor o como alumno. */
    long countByUserIdAndRoleInClass(UUID userId, EnrollmentRole role);

    /** Las clases de una persona, para su ficha en el panel de administración. */
    @EntityGraph(attributePaths = "schoolClass")
    List<Enrollment> findByUserIdOrderBySchoolClassNameAsc(UUID userId);

    /**
     * Alumnado distinto en las clases que imparte un profesor. DISTINCT porque
     * un mismo alumno puede estar en varias de sus clases y no debe contar dos veces.
     */
    @Query("""
            SELECT COUNT(DISTINCT alumno.user.id) FROM Enrollment alumno
            WHERE alumno.roleInClass = com.eduvibe.model.enums.EnrollmentRole.STUDENT
              AND alumno.schoolClass.id IN (
                  SELECT profesor.schoolClass.id FROM Enrollment profesor
                  WHERE profesor.user.id = :teacherId
                    AND profesor.roleInClass = com.eduvibe.model.enums.EnrollmentRole.TEACHER)
            """)
    long countStudentsForTeacher(@Param("teacherId") UUID teacherId);

    /**
     * Matriculaciones de varias clases en una sola consulta. Es lo que permite
     * pintar el panel de clases sin lanzar una consulta por tarjeta.
     */
    @EntityGraph(attributePaths = "user")
    List<Enrollment> findBySchoolClassIdIn(List<UUID> classIds);

    /** Eliminar a la persona la saca de todas las clases y grupos. */
    @Modifying
    @Query("DELETE FROM Enrollment e WHERE e.user.id = :userId")
    int deleteByUser(@Param("userId") UUID userId);
}
