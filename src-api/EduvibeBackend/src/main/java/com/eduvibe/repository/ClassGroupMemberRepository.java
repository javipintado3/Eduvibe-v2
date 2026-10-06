package com.eduvibe.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eduvibe.model.ClassGroupMember;

public interface ClassGroupMemberRepository extends JpaRepository<ClassGroupMember, UUID> {

    List<ClassGroupMember> findByUserId(UUID userId);

    @EntityGraph(attributePaths = "user")
    List<ClassGroupMember> findByClassGroupId(UUID groupId);

    List<ClassGroupMember> findByClassGroupIdIn(List<UUID> groupIds);

    void deleteByClassGroupId(UUID groupId);

    /** El subgrupo de un alumno en una clase concreta, si tiene uno. Como mucho hay uno por clase. */
    @Query("""
            SELECT m FROM ClassGroupMember m
            WHERE m.user.id = :userId AND m.classGroup.schoolClass.id = :classId
            """)
    Optional<ClassGroupMember> findDeAlumnoEnClase(@Param("userId") UUID userId, @Param("classId") UUID classId);
}
