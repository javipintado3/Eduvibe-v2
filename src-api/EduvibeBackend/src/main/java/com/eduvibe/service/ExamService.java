package com.eduvibe.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.dto.exam.AnswerRequest;
import com.eduvibe.dto.exam.BankQuestionResponse;
import com.eduvibe.dto.exam.ExamAttemptResponse;
import com.eduvibe.dto.exam.ExamAttemptSummaryResponse;
import com.eduvibe.dto.exam.ExamDetailResponse;
import com.eduvibe.dto.exam.ExamQuestionResponse;
import com.eduvibe.dto.exam.ExamResponse;
import com.eduvibe.dto.exam.ExamResultResponse;
import com.eduvibe.dto.exam.SaveExamRequest;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.model.Enrollment;
import com.eduvibe.model.Exam;
import com.eduvibe.model.ExamAnswer;
import com.eduvibe.model.ExamAttempt;
import com.eduvibe.model.ExamOption;
import com.eduvibe.model.ExamQuestion;
import com.eduvibe.model.Notification;
import com.eduvibe.model.SchoolClass;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.EnrollmentRole;
import com.eduvibe.repository.EnrollmentRepository;
import com.eduvibe.repository.ExamAnswerRepository;
import com.eduvibe.repository.ExamAttemptRepository;
import com.eduvibe.repository.ExamOptionRepository;
import com.eduvibe.repository.ExamQuestionRepository;
import com.eduvibe.repository.ExamRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

/**
 * Exámenes de opción múltiple de una clase: alta, consulta, y el intento del
 * alumnado con su corrección automática.
 *
 * Aparte de {@link AssignmentService} a propósito, igual que {@link Exam} está
 * aparte de {@link com.eduvibe.model.Assignment} en el modelo: quién puede
 * crearlos y editarlos lo decide {@link ClassAccessService}, con las mismas
 * reglas que una tarea.
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamOptionRepository examOptionRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final ExamAnswerRepository examAnswerRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassAccessService access;
    private final AuthService authService;
    private final TopicService topicService;
    private final NotificationService notificationService;

    // ------------------------------------------------------------ alta y consulta

    @Transactional
    public ExamDetailResponse create(UUID classId, SaveExamRequest request) {
        SchoolClass schoolClass = access.requireEditable(classId);
        AuthenticatedUser autenticado = authService.currentIdentity();

        User author = userRepository.findById(autenticado.id())
                .orElseThrow(() -> NotFoundException.of("Usuario", autenticado.id()));

        Exam exam = new Exam(schoolClass, request.title().trim(), request.description(),
                request.durationMinutes(), request.dueDate(), author);
        exam.setTopic(topicService.resolveOfSchoolClass(request.topicId(), classId));
        examRepository.saveAndFlush(exam);

        List<SaveExamRequest.QuestionInput> newQuestions = request.questionsOrNone();
        List<SaveExamRequest.ReuseQuestionInput> reused = request.reuseQuestionsOrNone();

        if (newQuestions.isEmpty() && reused.isEmpty()) {
            throw new BadRequestException("El examen necesita al menos una pregunta");
        }

        int totalPuntos = 0;
        int order = 0;

        for (SaveExamRequest.QuestionInput questionInput : newQuestions) {
            requireSingleCorrect(questionInput);

            ExamQuestion question = new ExamQuestion(exam, questionInput.text().trim(),
                    questionInput.pointsOrDefault(), order++);
            examQuestionRepository.save(question);
            totalPuntos += question.getPoints();

            int orderOption = 0;
            for (SaveExamRequest.OptionInput optionInput : questionInput.options()) {
                examOptionRepository.save(new ExamOption(question, optionInput.text().trim(),
                        optionInput.correct(), orderOption++));
            }
        }

        for (SaveExamRequest.ReuseQuestionInput reuseInput : reused) {
            totalPuntos += copyFromBank(exam, classId, reuseInput, order++);
        }

        notifyStudents(classId, schoolClass, exam);

        return ExamDetailResponse.of(exam, true, newQuestions.size() + reused.size(), totalPuntos, null, null);
    }

    /**
     * Copia una pregunta del banco de la clase en este examen, con sus
     * propias opciones. Es una copia y no una referencia compartida a
     * propósito: si el profesorado retoca la pregunta original más adelante,
     * los exámenes ya hechos no deben cambiar retroactivamente lo que el
     * alumnado ya respondió.
     */
    private int copyFromBank(Exam exam, UUID classId, SaveExamRequest.ReuseQuestionInput reuseInput, int order) {
        ExamQuestion original = examQuestionRepository.findById(reuseInput.questionId())
                .filter(q -> q.getExam().getSchoolClass().getId().equals(classId))
                .orElseThrow(() -> new BadRequestException("La pregunta indicada no es del banco de esta clase"));

        int points = reuseInput.pointsOrOriginal(original.getPoints());
        ExamQuestion copy = new ExamQuestion(exam, original.getText(), points, order);
        examQuestionRepository.save(copy);

        for (ExamOption option : examOptionRepository.findByQuestionIdOrderBySortOrderAsc(original.getId())) {
            examOptionRepository.save(new ExamOption(copy, option.getText(), option.isCorrect(), option.getSortOrder()));
        }

        return points;
    }

    /**
     * El banco de preguntas de la clase: todas las ya usadas en algún examen,
     * para reutilizarlas en uno nuevo sin volver a escribirlas.
     */
    @Transactional(readOnly = true)
    public List<BankQuestionResponse> listBankQuestions(UUID classId) {
        access.requireEditable(classId);

        List<ExamQuestion> bank = examQuestionRepository.findBankForSchoolClass(classId);
        Map<UUID, List<ExamOption>> optionsByQuestion = groupedOptionsByQuestion(bank);

        return bank.stream()
                .map(p -> BankQuestionResponse.of(p, optionsByQuestion.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    /** Cada pregunta necesita exactamente una opción correcta: ni cero ni dos. */
    private void requireSingleCorrect(SaveExamRequest.QuestionInput question) {
        long correct = question.options().stream().filter(SaveExamRequest.OptionInput::correct).count();
        if (correct != 1) {
            throw new BadRequestException(
                    "Cada pregunta necesita exactamente una opción correcta (\"" + question.text()
                            + "\" tiene " + correct + ")");
        }
    }

    private void notifyStudents(UUID classId, SchoolClass schoolClass, Exam exam) {
        List<User> alumnado = enrollmentRepository
                .findBySchoolClassIdAndRoleInClassOrderByUserNameAsc(classId, EnrollmentRole.STUDENT)
                .stream().map(Enrollment::getUser).toList();

        notificationService.issueForSeveral(alumnado, Notification.NEW_EXAM, Map.of(
                "className", schoolClass.getName(), "title", exam.getTitle(), "examId", exam.getId().toString()));
    }

    /**
     * Exámenes de una clase. Al profesorado le importan los intentos
     * recibidos; al alumnado, el estado de su propio intento.
     */
    @Transactional(readOnly = true)
    public List<ExamResponse> list(UUID classId) {
        access.requireVisible(classId);
        AuthenticatedUser user = authService.currentIdentity();

        List<Exam> exams = examRepository.findForSchoolClass(classId);
        if (exams.isEmpty()) {
            return List.of();
        }

        if (access.canGradeIn(classId)) {
            return exams.stream()
                    .map(exam -> ExamResponse.forTeacher(exam,
                            (int) examQuestionRepository.countByExamId(exam.getId()),
                            examAttemptRepository.countByExamId(exam.getId())))
                    .toList();
        }

        List<UUID> examIds = exams.stream().map(Exam::getId).toList();
        Map<UUID, ExamAttempt> myAttempts = new HashMap<>();
        for (ExamAttempt attempt : examAttemptRepository.findByExamIdInAndStudentId(examIds, user.id())) {
            myAttempts.put(attempt.getExam().getId(), attempt);
        }

        return exams.stream().map(exam -> {
            ExamAttempt attempt = myAttempts.get(exam.getId());
            return ExamResponse.forStudent(exam,
                    (int) examQuestionRepository.countByExamId(exam.getId()),
                    statusOf(attempt),
                    attempt != null && attempt.isSubmitted() ? attempt.getScore() : null);
        }).toList();
    }

    @Transactional(readOnly = true)
    public ExamDetailResponse detail(UUID examId) {
        Exam exam = findVisible(examId);
        UUID classId = exam.getSchoolClass().getId();
        boolean puedoEditar = access.canGradeIn(classId);

        int numeroPreguntas = (int) examQuestionRepository.countByExamId(examId);
        int totalPuntos = sumOfPoints(examId);

        if (puedoEditar) {
            return ExamDetailResponse.of(exam, true, numeroPreguntas, totalPuntos, null, null);
        }

        AuthenticatedUser user = authService.currentIdentity();
        ExamAttempt myAttempt = examAttemptRepository.findByExamIdAndStudentId(examId, user.id()).orElse(null);

        return ExamDetailResponse.of(exam, false, numeroPreguntas, totalPuntos,
                statusOf(myAttempt), myAttempt != null && myAttempt.isSubmitted() ? myAttempt.getScore() : null);
    }

    /** Las preguntas con sus respuestas correctas: solo para el profesorado, vista de repaso. */
    @Transactional(readOnly = true)
    public List<ExamQuestionResponse> preguntas(UUID examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> NotFoundException.of("Examen", examId));
        access.requireEditable(exam.getSchoolClass().getId());

        List<ExamQuestion> preguntas = examQuestionRepository.findByExamIdOrderBySortOrderAsc(examId);
        Map<UUID, List<ExamOption>> optionsByQuestion = groupedOptionsByQuestion(preguntas);

        return preguntas.stream()
                .map(p -> ExamQuestionResponse.of(p, optionsByQuestion.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    /** Borra un examen. Solo se permite mientras nadie lo haya empezado. */
    @Transactional
    public void delete(UUID examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> NotFoundException.of("Examen", examId));
        access.requireEditable(exam.getSchoolClass().getId());

        long attempts = examAttemptRepository.countByExamId(examId);
        if (attempts > 0) {
            throw new BadRequestException(
                    "No se puede borrar: ya hay " + attempts + " intento(s) registrado(s).");
        }

        examRepository.delete(exam);
    }

    // ---------------------------------------------------------------- intento

    /**
     * Empieza el examen, o retoma el intento en curso si ya se había
     * empezado: llamarlo dos veces no crea dos intentos.
     */
    @Transactional
    public ExamAttemptResponse startOrResume(UUID examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> NotFoundException.of("Examen", examId));
        UUID classId = exam.getSchoolClass().getId();
        access.requireStudentOf(classId);

        AuthenticatedUser autenticado = authService.currentIdentity();
        User student = userRepository.findById(autenticado.id())
                .orElseThrow(() -> NotFoundException.of("Usuario", autenticado.id()));

        ExamAttempt attempt = examAttemptRepository.findByExamIdAndStudentId(examId, student.getId()).orElse(null);

        if (attempt == null) {
            if (exam.haVencido()) {
                throw new BadRequestException("Ya no se puede empezar este examen: pasó la fecha límite");
            }
            attempt = new ExamAttempt(exam, student);
            examAttemptRepository.saveAndFlush(attempt);
        } else if (attempt.isSubmitted()) {
            throw new BadRequestException("Ya entregaste este examen");
        }

        return buildAttemptResponse(exam, attempt);
    }

    private ExamAttemptResponse buildAttemptResponse(Exam exam, ExamAttempt attempt) {
        List<ExamQuestion> preguntas = shuffleByAttempt(
                examQuestionRepository.findByExamIdOrderBySortOrderAsc(exam.getId()), attempt.getId());
        Map<UUID, List<ExamOption>> optionsByQuestion = groupedOptionsByQuestion(preguntas);

        Map<UUID, UUID> myAnswers = new HashMap<>();
        for (ExamAnswer answer : examAnswerRepository.findByAttemptId(attempt.getId())) {
            if (answer.getSelectedOption() != null) {
                myAnswers.put(answer.getQuestion().getId(), answer.getSelectedOption().getId());
            }
        }

        List<ExamAttemptResponse.QuestionResponse> questionsAnswer = preguntas.stream()
                .map(p -> new ExamAttemptResponse.QuestionResponse(
                        p.getId(), p.getText(), p.getPoints(),
                        shuffleByAttemptAndQuestion(optionsByQuestion.getOrDefault(p.getId(), List.of()),
                                attempt.getId(), p.getId())
                                .stream()
                                .map(o -> new ExamAttemptResponse.OptionResponse(o.getId(), o.getText()))
                                .toList(),
                        myAnswers.get(p.getId())))
                .toList();

        return new ExamAttemptResponse(attempt.getId(), exam.getId(), exam.getTitle(),
                exam.getDurationMinutes(), attempt.getStartedAt(), attempt.timeLimit(),
                attempt.isSubmitted(), questionsAnswer);
    }

    /** Guarda o cambia la respuesta a una pregunta. Autoguardado: se llama en cada clic. */
    @Transactional
    public void saveAnswer(UUID examId, UUID questionId, AnswerRequest request) {
        ExamAttempt attempt = myAttemptInProgress(examId);

        ExamQuestion question = examQuestionRepository.findById(questionId)
                .filter(p -> p.getExam().getId().equals(examId))
                .orElseThrow(() -> NotFoundException.of("Pregunta", questionId));

        ExamOption option = null;
        if (request.selectedOptionId() != null) {
            option = examOptionRepository.findById(request.selectedOptionId())
                    .filter(o -> o.getQuestion().getId().equals(questionId))
                    .orElseThrow(() -> new BadRequestException("La opción indicada no es de esta pregunta"));
        }

        ExamAnswer answer = examAnswerRepository.findByAttemptIdAndQuestionId(attempt.getId(), questionId)
                .orElseGet(() -> new ExamAnswer(attempt, question, null));

        answer.setSelectedOption(option);
        examAnswerRepository.save(answer);
    }

    /**
     * Entrega el intento y lo corrige al momento: cada pregunta acertada suma
     * sus puntos, las demás no restan.
     */
    @Transactional
    public ExamResultResponse submit(UUID examId) {
        ExamAttempt attempt = myAttemptInProgress(examId);

        List<ExamQuestion> preguntas = shuffleByAttempt(
                examQuestionRepository.findByExamIdOrderBySortOrderAsc(examId), attempt.getId());
        Map<UUID, ExamAnswer> answerByQuestion = new HashMap<>();
        for (ExamAnswer answer : examAnswerRepository.findByAttemptId(attempt.getId())) {
            answerByQuestion.put(answer.getQuestion().getId(), answer);
        }

        Map<UUID, UUID> correctByQuestion = new HashMap<>();
        for (ExamOption option : examOptionRepository
                .findByQuestionIdIn(preguntas.stream().map(ExamQuestion::getId).toList())) {
            if (option.isCorrect()) {
                correctByQuestion.put(option.getQuestion().getId(), option.getId());
            }
        }

        BigDecimal grade = BigDecimal.ZERO;
        List<ExamResultResponse.QuestionResultResponse> breakdown = new ArrayList<>();

        for (ExamQuestion question : preguntas) {
            ExamAnswer answer = answerByQuestion.get(question.getId());
            boolean acerto = answer != null && answer.isCorrect();
            if (acerto) {
                grade = grade.add(BigDecimal.valueOf(question.getPoints()));
            }

            UUID miRespuesta = answer == null || answer.getSelectedOption() == null
                    ? null : answer.getSelectedOption().getId();

            breakdown.add(new ExamResultResponse.QuestionResultResponse(
                    question.getId(), question.getText(), question.getPoints(),
                    miRespuesta, correctByQuestion.get(question.getId()), acerto));
        }

        attempt.submit(grade);
        examAttemptRepository.save(attempt);

        int totalPuntos = preguntas.stream().mapToInt(ExamQuestion::getPoints).sum();
        return new ExamResultResponse(attempt.getId(), grade, totalPuntos, attempt.getSubmittedAt(), breakdown);
    }

    /** Todos los intentos de un examen, para la vista de corrección del profesorado. */
    @Transactional(readOnly = true)
    public List<ExamAttemptSummaryResponse> listAttempts(UUID examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> NotFoundException.of("Examen", examId));
        access.requireEditable(exam.getSchoolClass().getId());

        return examAttemptRepository.findByExamIdOrderByStudentNameAsc(examId).stream()
                .map(attempt -> new ExamAttemptSummaryResponse(
                        attempt.getId(), attempt.getStudent().getId(), attempt.getStudent().getName(),
                        attempt.getStartedAt(), attempt.getSubmittedAt(), attempt.getScore(),
                        attempt.isSubmitted()))
                .toList();
    }

    // ------------------------------------------------------------------- ayuda

    /** Busca un examen comprobando que quien pregunta pueda ver su clase. */
    private Exam findVisible(UUID examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> NotFoundException.of("Examen", examId));
        access.requireVisible(exam.getSchoolClass().getId());
        return exam;
    }

    /** El intento de quien pregunta, si sigue a tiempo y sin entregar. */
    private ExamAttempt myAttemptInProgress(UUID examId) {
        AuthenticatedUser autenticado = authService.currentIdentity();
        ExamAttempt attempt = examAttemptRepository.findByExamIdAndStudentId(examId, autenticado.id())
                .orElseThrow(() -> new BadRequestException("Todavía no has empezado este examen"));

        if (attempt.isSubmitted()) {
            throw new BadRequestException("Ya entregaste este examen, no puedes cambiar tus respuestas");
        }
        if (Instant.now().isAfter(attempt.timeLimit())) {
            throw new BadRequestException("Se acabó el tiempo para este examen");
        }
        return attempt;
    }

    /**
     * Baraja una lista con una semilla fija por intento: cada alumno ve sus
     * preguntas en un orden distinto, pero recargar la página o volver a
     * entregar no las reordena otra vez, porque la semilla es siempre la
     * misma para ese intento. No hace falta guardar el orden en ningún sitio.
     */
    private <T> List<T> shuffleByAttempt(List<T> list, UUID attemptId) {
        List<T> copy = new ArrayList<>(list);
        Collections.shuffle(copy, new Random(attemptId.getMostSignificantBits()));
        return copy;
    }

    /** Igual que {@link #shuffleByAttempt}, pero con una semilla propia por pregunta: cada una baraja sus opciones distinto. */
    private <T> List<T> shuffleByAttemptAndQuestion(List<T> list, UUID attemptId, UUID questionId) {
        List<T> copy = new ArrayList<>(list);
        Collections.shuffle(copy, new Random(attemptId.getMostSignificantBits() ^ questionId.getMostSignificantBits()));
        return copy;
    }

    private Map<UUID, List<ExamOption>> groupedOptionsByQuestion(List<ExamQuestion> preguntas) {
        if (preguntas.isEmpty()) {
            return Map.of();
        }
        List<UUID> questionIds = preguntas.stream().map(ExamQuestion::getId).toList();

        Map<UUID, List<ExamOption>> optionsByQuestion = new HashMap<>();
        for (ExamOption option : examOptionRepository.findByQuestionIdIn(questionIds)) {
            optionsByQuestion.computeIfAbsent(option.getQuestion().getId(), k -> new ArrayList<>()).add(option);
        }
        return optionsByQuestion;
    }

    private int sumOfPoints(UUID examId) {
        return examQuestionRepository.findByExamIdOrderBySortOrderAsc(examId).stream()
                .mapToInt(ExamQuestion::getPoints)
                .sum();
    }

    private String statusOf(ExamAttempt attempt) {
        if (attempt == null) {
            return "no_empezado";
        }
        return attempt.isSubmitted() ? "entregado" : "en_curso";
    }
}
