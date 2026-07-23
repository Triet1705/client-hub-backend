package com.clienthub.domain.repository;

import com.clienthub.domain.entity.Attachment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    @EntityGraph(attributePaths = "uploader")
    Optional<Attachment> findByIdAndTenantId(UUID id, String tenantId);
}
