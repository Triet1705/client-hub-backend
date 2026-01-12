package com.clienthub.core.repository;

import com.clienthub.core.domain.entity.Project;
import com.clienthub.core.domain.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {


    @EntityGraph(attributePaths = {"owner"})
    @Query("SELECT p FROM Project p WHERE p.tenantId = :tenantId")
    List<Project> findAllByTenantId(@Param("tenantId") String tenantId);

    @EntityGraph(attributePaths = {"owner"})
    @Query("SELECT p FROM Project p WHERE p.id = :id AND p.tenantId = :tenantId")
    Optional<Project> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") String tenantId);

    @EntityGraph(attributePaths = {"owner"})
    @Query("SELECT p FROM Project p WHERE p.tenantId = :tenantId")
    Page<Project> findAllByTenantId(@Param("tenantId") String tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"owner"})
    @Query("SELECT p FROM Project p WHERE p.tenantId = :tenantId AND p.status = :status")
    Page<Project> findByTenantIdAndStatus(
        @Param("tenantId") String tenantId,
        @Param("status") ProjectStatus status,
        Pageable pageable
    );

    @Query("SELECT COUNT(p) FROM Project p WHERE p.tenantId = :tenantId AND p.status = :status")
    long countByTenantIdAndStatus(
        @Param("tenantId") String tenantId,
        @Param("status") ProjectStatus status
    );

    boolean existsByIdAndTenantId(UUID id, String tenantId);
}

