package com.clienthub.core.mapper;

import com.clienthub.core.domain.entity.Project;
import com.clienthub.core.dto.project.ProjectRequest;
import com.clienthub.core.dto.project.ProjectResponse;

public interface ProjectMapper {
    ProjectResponse toResponse(Project project);
    Project toEntity(ProjectRequest request);
    void updateEntityFromRequest(ProjectRequest request, Project project);
}
