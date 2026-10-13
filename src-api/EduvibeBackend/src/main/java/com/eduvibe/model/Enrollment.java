package com.eduvibe.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.eduvibe.model.enums.EnrollmentRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vínculo entre una persona y una clase, con el papel que desempeña en ella.
 *
 * Es la tabla que responde a las tres preguntas que la aplicación hace
 * constantemente: qué clases tiene alguien, quién está en una clase, y si una
 * persona puede ver o editar lo que hay dentro.
 */
@Entity
@Table(name = "enrollments")
@Getter
@Setter
@NoArgsConstructor
public class Enrollment {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "role_in_class", nullable = false, length = 20)
    private EnrollmentRole roleInClass;

    @Generated(event = EventType.INSERT)
    @Column(name = "enrolled_at", insertable = false, updatable = false)
    private Instant enrolledAt;

    public Enrollment(SchoolClass schoolClass, User user, EnrollmentRole roleInClass) {
        this.schoolClass = schoolClass;
        this.user = user;
        this.roleInClass = roleInClass;
    }

    public boolean isTeacher() {
        return roleInClass == EnrollmentRole.TEACHER;
    }
}
