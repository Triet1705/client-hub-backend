package com.clienthub.core.repository;

import com.clienthub.core.domain.entity.Task;
import com.clienthub.core.domain.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    @Query("SELECT t FROM Task t WHERE t.project.id = :projectId AND t.tenantId = :tenantId")
    Page<Task> findByProjectIdAndTenantId(
            @Param("projectId") UUID projectId,
            @Param("tenantId") String tenantId,
            Pageable pageable
    );

    @Query("SELECT t FROM Task t WHERE t.project.id = :projectId AND t.status = :status AND t.tenantId = :tenantId")
    Page<Task> findByProjectIdAndStatusAndTenantId(
            @Param("projectId") UUID projectId,
            @Param("status") TaskStatus status,
            @Param("tenantId") String tenantId,
            Pageable pageable
    );

    @Query("SELECT t FROM Task t WHERE t.assignedTo.id = :userId AND t.tenantId = :tenantId")
    Page<Task> findByAssignedToIdAndTenantId(
            @Param("userId") UUID userId,
            @Param("tenantId") String tenantId,
            Pageable pageable
    );

    @Query("SELECT t FROM Task t WHERE t.tenantId = :tenantId")
    Page<Task> findAllByTenantId(
            @Param("tenantId") String tenantId,
            Pageable pageable
    );

    @Query("SELECT t FROM Task t WHERE t.id = :id AND t.tenantId = :tenantId")
    Optional<Task> findByIdAndTenantId(
            @Param("id") UUID id,
            @Param("tenantId") String tenantId);

    @Query("SELECT t FROM Task t " +
            "LEFT JOIN FETCH t.project " +
            "LEFT JOIN FETCH t.assignedTo " +
            "WHERE t.tenantId = :tenantId")
    List<Task> findAllByTenantIdWithRelations(@Param("tenantId") String tenantId);
}
