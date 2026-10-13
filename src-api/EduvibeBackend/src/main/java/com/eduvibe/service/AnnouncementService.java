package com.eduvibe.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.dto.announcement.AnnouncementResponse;
import com.eduvibe.dto.announcement.SaveAnnouncementRequest;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.model.Announcement;
import com.eduvibe.model.Enrollment;
import com.eduvibe.model.Notification;
import com.eduvibe.model.SchoolClass;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.EnrollmentRole;
import com.eduvibe.repository.AnnouncementRepository;
import com.eduvibe.repository.EnrollmentRepository;
import com.eduvibe.repository.UserRepository;
import com.eduvibe.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import com.eduvibe.dto.common.PageResponse;

/**
 * Muro de avisos de una clase.
 *
 * Solo el profesorado de la clase (o administración) publica y retira avisos;
 * lo decide {@link ClassAccessService#requireEditable}, igual que para tareas y
 * temas. El alumnado matriculado solo lee.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassAccessService access;
    private final AuthService authService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public PageResponse<AnnouncementResponse> list(UUID classId, Pageable pageable) {
        access.requireVisible(classId);
        boolean puedoBorrar = access.canGradeIn(classId);

        return PageResponse.of(
                announcementRepository.findBySchoolClassIdOrderByPinnedDescCreatedAtDesc(
                        classId, Pagination.unsorted(pageable)),
                announcement -> AnnouncementResponse.of(announcement, puedoBorrar));
    }

    @Transactional
    public AnnouncementResponse create(UUID classId, SaveAnnouncementRequest request) {
        SchoolClass schoolClass = access.requireEditable(classId);
        AuthenticatedUser autenticado = authService.currentIdentity();

        User author = userRepository.findById(autenticado.id())
                .orElseThrow(() -> NotFoundException.of("Usuario", autenticado.id()));

        Announcement announcement = new Announcement(schoolClass, author, request.content().trim(), request.isPinned());
        announcementRepository.saveAndFlush(announcement);

        List<User> alumnado = enrollmentRepository
                .findBySchoolClassIdAndRoleInClassOrderByUserNameAsc(classId, EnrollmentRole.STUDENT)
                .stream().map(Enrollment::getUser).toList();
        notificationService.issueForSeveral(alumnado, Notification.NEW_ANNOUNCEMENT, Map.of(
                "className", schoolClass.getName(), "classId", classId.toString()));

        return AnnouncementResponse.of(announcement, true);
    }

    @Transactional
    public void delete(UUID announcementId) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .orElseThrow(() -> NotFoundException.of("Aviso", announcementId));

        access.requireEditable(announcement.getSchoolClass().getId());

        announcementRepository.delete(announcement);
    }
}
