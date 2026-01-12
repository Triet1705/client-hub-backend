package com.clienthub.core.mapper;

import com.clienthub.core.domain.entity.Project;
import com.clienthub.core.dto.project.ProjectRequest;
import com.clienthub.core.dto.project.ProjectResponse;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

@Component
public class ProjectMapperImpl implements ProjectMapper {

    @Override
    public ProjectResponse toResponse(Project project) {
        if (project == null) {
            return null;
        }

        ProjectResponse response = new ProjectResponse();
        response.setId(project.getId());
        response.setTitle(project.getTitle());
        response.setDescription(project.getDescription());
        response.setStatus(project.getStatus());
        response.setDeadline(project.getDeadline());
        response.setBudget(project.getBudget());
        
        if (project.getOwner() != null) {
            response.setOwnerId(project.getOwner().getId());
            response.setOwnerEmail(project.getOwner().getEmail());
            response.setOwnerName(project.getOwner().getFullName());
        }
        
        if (project.getCreatedAt() != null) {
            response.setCreatedAt(project.getCreatedAt().toInstant(ZoneOffset.UTC));
        }
        if (project.getUpdateAt() != null) {
            response.setUpdatedAt(project.getUpdateAt().toInstant(ZoneOffset.UTC));
        }
        
        return response;
    }

    @Override
    public Project toEntity(ProjectRequest request) {
        if (request == null) {
            return null;
        }

        Project project = new Project();
        project.setTitle(request.getTitle());
        project.setDescription(request.getDescription());
        project.setStatus(request.getStatus());
        project.setDeadline(request.getDeadline());
        project.setBudget(request.getBudget());
        
        return project;
    }

    @Override
    public void updateEntityFromRequest(ProjectRequest request, Project project) {
        if (request == null || project == null) {
            return;
        }

        if (request.getTitle() != null) {
            project.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            project.setStatus(request.getStatus());
        }
        if (request.getDeadline() != null) {
            project.setDeadline(request.getDeadline());
        }
        if (request.getBudget() != null) {
            project.setBudget(request.getBudget());
        }
    }
}
