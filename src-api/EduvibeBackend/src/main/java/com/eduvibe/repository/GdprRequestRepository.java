package com.eduvibe.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eduvibe.model.GdprRequest;

public interface GdprRequestRepository extends JpaRepository<GdprRequest, UUID> {
}
