package com.clienthub.application.mapper;

import com.clienthub.domain.entity.Project;
import com.clienthub.application.dto.project.ProjectRequest;
import com.clienthub.application.dto.project.ProjectResponse;

public interface ProjectMapper {
    ProjectResponse toResponse(Project project);
    Project toEntity(ProjectRequest request);
    void updateEntityFromRequest(ProjectRequest request, Project project);
}
