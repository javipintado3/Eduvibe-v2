package com.eduvibe.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.eduvibe.model.enums.ResourceType;

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
 * Material de clase: apuntes, un enlace, un vídeo.
 *
 * Como las tareas, puede colgar de un tema, y así el trabajo de clase queda
 * ordenado por unidades en lugar de ser una lista plana.
 */
@Entity
@Table(name = "resources")
@Getter
@Setter
@NoArgsConstructor
public class Resource {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "type", length = 20)
    private ResourceType type;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Desde cuándo es visible para el alumnado. Null = visible desde que se publica. */
    @Column(name = "available_from")
    private Instant availableFrom;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = { EventType.INSERT, EventType.UPDATE })
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public Resource(SchoolClass schoolClass, String title, String fileUrl,
                    ResourceType type, int sortOrder) {
        this.schoolClass = schoolClass;
        this.title = title;
        this.fileUrl = fileUrl;
        this.type = type;
        this.sortOrder = sortOrder;
    }

    /** Si al alumnado todavía no le toca verlo. El profesorado lo ve siempre. */
    public boolean lockedForStudents() {
        return availableFrom != null && Instant.now().isBefore(availableFrom);
    }
}
