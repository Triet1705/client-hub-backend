package com.clienthub.domain.repository;

import com.clienthub.domain.entity.ProjectMember;
import com.clienthub.domain.entity.ProjectMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMemberId> {

    List<ProjectMember> findByIdProjectIdAndTenantId(UUID projectId, String tenantId);

    boolean existsByIdProjectIdAndIdUserIdAndTenantId(UUID projectId, UUID userId, String tenantId);

    Optional<ProjectMember> findByIdProjectIdAndIdUserIdAndTenantId(UUID projectId, UUID userId, String tenantId);
}
