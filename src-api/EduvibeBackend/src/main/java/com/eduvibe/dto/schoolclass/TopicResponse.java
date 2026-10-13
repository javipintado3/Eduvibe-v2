package com.eduvibe.dto.schoolclass;

import java.util.UUID;

import com.eduvibe.model.Topic;

public record TopicResponse(UUID id, String title, int sortOrder) {

    public static TopicResponse of(Topic topic) {
        return new TopicResponse(topic.getId(), topic.getTitle(), topic.getSortOrder());
    }
}
