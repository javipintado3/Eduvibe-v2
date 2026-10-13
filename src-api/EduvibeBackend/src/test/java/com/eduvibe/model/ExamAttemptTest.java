package com.eduvibe.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.eduvibe.model.enums.UserRole;

class ExamAttemptTest {

    private final Organization organization = new Organization("Centro", null);
    private final User teacher = new User(organization, "profe@centro.es", "Profe", UserRole.TEACHER);
    private final User student = new User(organization, "ana@centro.es", "Ana", UserRole.STUDENT);

    private Exam exam(int durationMinutes) {
        SchoolClass schoolClass = new SchoolClass(organization, "1º A", "Matemáticas", "#2563eb", null);
        return new Exam(schoolClass, "Examen 1", null, durationMinutes, null, teacher);
    }

    @Nested
    @DisplayName("Estado del intento")
    class Statuses {

        @Test
        @DisplayName("nace sin entregar y sin nota")
        void naceSinEntregar() {
            ExamAttempt attempt = new ExamAttempt(exam(30), student);

            assertThat(attempt.isSubmitted()).isFalse();
            assertThat(attempt.getScore()).isNull();
            assertThat(attempt.getStartedAt()).isNotNull();
        }

        @Test
        @DisplayName("al entregar se fijan la fecha y la nota")
        void alEntregarSeFijanFechaYNota() {
            ExamAttempt attempt = new ExamAttempt(exam(30), student);

            attempt.submit(BigDecimal.valueOf(7));

            assertThat(attempt.isSubmitted()).isTrue();
            assertThat(attempt.getSubmittedAt()).isNotNull();
            assertThat(attempt.getScore()).isEqualByComparingTo("7");
        }
    }

    @Nested
    @DisplayName("Límite de tiempo")
    class TimeLimit {

        @Test
        @DisplayName("se calcula sumando la duración del examen al inicio del intento")
        void seCalculaDesdeElInicio() {
            ExamAttempt attempt = new ExamAttempt(exam(45), student);

            Instant limit = attempt.timeLimit();

            assertThat(limit).isEqualTo(attempt.getStartedAt().plusSeconds(45 * 60L));
        }
    }

    @Nested
    @DisplayName("Corrección de una respuesta")
    class Grading {

        @Test
        @DisplayName("una respuesta con la opción correcta marcada es correcta")
        void respuestaConOpcionCorrecta() {
            Exam exam = exam(30);
            ExamQuestion question = new ExamQuestion(exam, "¿2+2?", 1, 0);
            ExamOption correct = new ExamOption(question, "4", true, 0);
            ExamAttempt attempt = new ExamAttempt(exam, student);

            ExamAnswer answer = new ExamAnswer(attempt, question, correct);

            assertThat(answer.isCorrect()).isTrue();
        }

        @Test
        @DisplayName("una respuesta con una opción incorrecta no es correcta")
        void respuestaConOpcionIncorrecta() {
            Exam exam = exam(30);
            ExamQuestion question = new ExamQuestion(exam, "¿2+2?", 1, 0);
            ExamOption incorrect = new ExamOption(question, "5", false, 1);
            ExamAttempt attempt = new ExamAttempt(exam, student);

            ExamAnswer answer = new ExamAnswer(attempt, question, incorrect);

            assertThat(answer.isCorrect()).isFalse();
        }

        @Test
        @DisplayName("una pregunta sin responder no es correcta")
        void preguntaSinResponderNoEsCorrecta() {
            Exam exam = exam(30);
            ExamQuestion question = new ExamQuestion(exam, "¿2+2?", 1, 0);
            ExamAttempt attempt = new ExamAttempt(exam, student);

            ExamAnswer answer = new ExamAnswer(attempt, question, null);

            assertThat(answer.isCorrect()).isFalse();
        }
    }
}
