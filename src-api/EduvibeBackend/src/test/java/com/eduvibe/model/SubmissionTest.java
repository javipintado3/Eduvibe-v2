package com.eduvibe.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.eduvibe.model.enums.SubmissionStatus;
import com.eduvibe.model.enums.UserRole;

class SubmissionTest {

    private final Organization organization = new Organization("Centro", null);
    private final User teacher = new User(organization, "profe@centro.es", "Profe", UserRole.TEACHER);
    private final User student = new User(organization, "ana@centro.es", "Ana", UserRole.STUDENT);

    private Assignment assignmentWithDeadline(Duration fromNow) {
        SchoolClass schoolClass = new SchoolClass(organization, "1º A", "Matemáticas", "#2563eb", null);
        return new Assignment(schoolClass, "Ejercicios", null, Instant.now().plus(fromNow), 100, teacher);
    }

    private Assignment assignmentWithoutDeadline() {
        SchoolClass schoolClass = new SchoolClass(organization, "1º A", "Matemáticas", "#2563eb", null);
        return new Assignment(schoolClass, "Ejercicios", null, null, 100, teacher);
    }

    @Nested
    @DisplayName("Estados de la entrega")
    class Statuses {

        @Test
        @DisplayName("nace como borrador y sin fecha de envío")
        void naceBorrador() {
            Submission submission = new Submission(assignmentWithoutDeadline(), student);

            assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.DRAFT);
            assertThat(submission.getSubmittedAt()).isNull();
            assertThat(submission.isDraft()).isTrue();
        }

        @Test
        @DisplayName("al enviar se fija la fecha de envío")
        void alEnviarSeFijaLaFecha() {
            Submission submission = new Submission(assignmentWithoutDeadline(), student);

            submission.enviar("Mi respuesta", null);

            assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
            assertThat(submission.getSubmittedAt()).isNotNull();
        }

        @Test
        @DisplayName("volver a borrador borra la fecha de envío")
        void volverABorradorLimpiaLaFecha() {
            Submission submission = new Submission(assignmentWithoutDeadline(), student);
            submission.enviar("Algo", null);

            submission.saveDraft("Sigo trabajando", null);

            // La base de datos exige fecha de envío en todo lo que no sea
            // borrador, y la inversa: un borrador no puede tenerla
            assertThat(submission.getSubmittedAt()).isNull();
            assertThat(submission.isDraft()).isTrue();
        }
    }

    @Nested
    @DisplayName("Entrega fuera de plazo")
    class OutsideOfDeadline {

        @Test
        @DisplayName("un borrador nunca consta como tarde: todavía no se ha entregado")
        void borradorNoEsTarde() {
            Submission submission = new Submission(assignmentWithDeadline(Duration.ofDays(-5)), student);

            assertThat(submission.entregadaTarde()).isFalse();
        }

        @Test
        @DisplayName("entregar dentro de plazo no es tarde")
        void dentroDePlazo() {
            Submission submission = new Submission(assignmentWithDeadline(Duration.ofDays(3)), student);

            submission.enviar("A tiempo", null);

            assertThat(submission.entregadaTarde()).isFalse();
        }

        @Test
        @DisplayName("entregar pasado el plazo sí es tarde")
        void pasadoElPlazo() {
            Submission submission = new Submission(assignmentWithDeadline(Duration.ofDays(-1)), student);

            submission.enviar("Se me pasó", null);

            assertThat(submission.entregadaTarde()).isTrue();
        }

        @Test
        @DisplayName("una tarea sin plazo no se entrega tarde nunca")
        void sinPlazoNuncaEsTarde() {
            Submission submission = new Submission(assignmentWithoutDeadline(), student);

            submission.enviar("Cuando sea", null);

            assertThat(submission.entregadaTarde()).isFalse();
        }

        @Test
        @DisplayName("sigue constando como tarde después de calificarla")
        void calificarNoBorraElRetraso() {
            Submission submission = new Submission(assignmentWithDeadline(Duration.ofDays(-2)), student);
            submission.enviar("Tarde", null);

            submission.markAsGraded();

            // Este es justo el motivo de que 'late' no sea un estado más: si lo
            // fuese, calificar la entrega borraría el dato de que llegó tarde
            assertThat(submission.isGraded()).isTrue();
            assertThat(submission.entregadaTarde()).isTrue();
        }
    }
}
