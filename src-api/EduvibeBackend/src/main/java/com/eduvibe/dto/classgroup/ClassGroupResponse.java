package com.eduvibe.dto.classgroup;

import java.util.List;
import java.util.UUID;

import com.eduvibe.model.ClassGroup;
import com.eduvibe.model.ClassGroupMember;

public record ClassGroupResponse(
        UUID id,
        String name,
        List<Member> members) {

    public static ClassGroupResponse of(ClassGroup group, List<ClassGroupMember> members) {
        return new ClassGroupResponse(
                group.getId(),
                group.getName(),
                members.stream().map(Member::of).toList());
    }

    public record Member(UUID id, String name) {
        public static Member of(ClassGroupMember member) {
            return new Member(member.getUser().getId(), member.getUser().getName());
        }
    }
}
