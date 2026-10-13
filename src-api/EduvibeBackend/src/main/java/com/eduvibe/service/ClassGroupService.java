package com.eduvibe.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eduvibe.dto.classgroup.ClassGroupResponse;
import com.eduvibe.dto.classgroup.SaveClassGroupRequest;
import com.eduvibe.exception.BadRequestException;
import com.eduvibe.exception.NotFoundException;
import com.eduvibe.model.ClassGroup;
import com.eduvibe.model.ClassGroupMember;
import com.eduvibe.model.SchoolClass;
import com.eduvibe.model.User;
import com.eduvibe.model.enums.EnrollmentRole;
import com.eduvibe.repository.ClassGroupMemberRepository;
import com.eduvibe.repository.ClassGroupRepository;
import com.eduvibe.repository.EnrollmentRepository;
import com.eduvibe.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Subgrupos de una clase: quién trabaja con quién.
 *
 * Solo el profesorado de la clase (o administración) los gestiona, igual que
 * avisos, materiales o tareas; lo alumnado los ve, pero no los edita.
 */
@Service
@RequiredArgsConstructor
public class ClassGroupService {

    private final ClassGroupRepository classGroupRepository;
    private final ClassGroupMemberRepository classGroupMemberRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final ClassAccessService access;

    @Transactional(readOnly = true)
    public List<ClassGroupResponse> list(UUID classId) {
        access.requireVisible(classId);

        List<ClassGroup> groups = classGroupRepository.findBySchoolClassIdOrderByNameAsc(classId);
        if (groups.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = groups.stream().map(ClassGroup::getId).toList();
        Map<UUID, List<ClassGroupMember>> membersByGroup = classGroupMemberRepository.findByClassGroupIdIn(ids)
                .stream()
                .collect(Collectors.groupingBy(m -> m.getClassGroup().getId()));

        return groups.stream()
                .map(group -> ClassGroupResponse.of(group, membersByGroup.getOrDefault(group.getId(), List.of())))
                .toList();
    }

    @Transactional
    public ClassGroupResponse create(UUID classId, SaveClassGroupRequest request) {
        SchoolClass schoolClass = access.requireEditable(classId);

        ClassGroup group = new ClassGroup(schoolClass, request.name().trim());
        classGroupRepository.saveAndFlush(group);

        List<ClassGroupMember> members = assignMembers(group, classId, request.memberIds());
        return ClassGroupResponse.of(group, members);
    }

    /** Reemplaza el nombre y los miembros enteros, igual que una rúbrica reemplaza sus criterios. */
    @Transactional
    public ClassGroupResponse update(UUID groupId, SaveClassGroupRequest request) {
        ClassGroup group = classGroupRepository.findById(groupId)
                .orElseThrow(() -> NotFoundException.of("Subgrupo", groupId));

        UUID classId = group.getSchoolClass().getId();
        access.requireEditable(classId);

        group.setName(request.name().trim());
        classGroupRepository.save(group);

        classGroupMemberRepository.deleteByClassGroupId(groupId);
        List<ClassGroupMember> members = assignMembers(group, classId, request.memberIds());

        return ClassGroupResponse.of(group, members);
    }

    /**
     * El subgrupo de un alumno en una clase, para adjuntarlo a una tarea
     * grupal. Null si no está en ninguno todavía.
     */
    @Transactional(readOnly = true)
    public ClassGroupResponse myGroupInSchoolClass(UUID userId, UUID classId) {
        return classGroupMemberRepository.findForStudentInSchoolClass(userId, classId)
                .map(membership -> {
                    ClassGroup group = membership.getClassGroup();
                    return ClassGroupResponse.of(group, classGroupMemberRepository.findByClassGroupId(group.getId()));
                })
                .orElse(null);
    }

    @Transactional
    public void delete(UUID groupId) {
        ClassGroup group = classGroupRepository.findById(groupId)
                .orElseThrow(() -> NotFoundException.of("Subgrupo", groupId));

        access.requireEditable(group.getSchoolClass().getId());
        classGroupRepository.delete(group);
    }

    /**
     * Valida que cada miembro esté matriculado como alumno de la clase —sin
     * esto, se podría meter en un subgrupo a cualquiera solo con conocer su
     * identificador— y crea las filas de pertenencia.
     */
    private List<ClassGroupMember> assignMembers(ClassGroup group, UUID classId, List<UUID> memberIds) {
        List<ClassGroupMember> members = new ArrayList<>();
        for (UUID userId : memberIds) {
            boolean isStudent = enrollmentRepository
                    .existsBySchoolClassIdAndUserIdAndRoleInClass(classId, userId, EnrollmentRole.STUDENT);
            if (!isStudent) {
                throw new BadRequestException("Todos los miembros deben ser alumnado matriculado en esta clase");
            }
            User student = userRepository.findById(userId)
                    .orElseThrow(() -> NotFoundException.of("Usuario", userId));
            members.add(new ClassGroupMember(group, student));
        }
        classGroupMemberRepository.saveAll(members);
        return members;
    }
}
