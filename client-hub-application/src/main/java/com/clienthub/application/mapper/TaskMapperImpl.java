package com.clienthub.application.mapper;

import com.clienthub.domain.entity.Task;
import com.clienthub.domain.entity.User;
import com.clienthub.application.dto.UserSummaryDto;
import com.clienthub.application.dto.task.TaskRequest;
import com.clienthub.application.dto.task.TaskResponse;
import org.springframework.stereotype.Component;


@Component
public class TaskMapperImpl implements TaskMapper {

    @Override
    public TaskResponse toResponse(Task task) {
        if (task == null) {
            return null;
        }

        TaskResponse response = new TaskResponse();
        response.setId(task.getId());
        response.setTitle(task.getTitle());
        response.setDescription(task.getDescription());
        response.setStatus(task.getStatus());
        response.setPriority(task.getPriority());
        response.setEstimatedHours(task.getEstimatedHours());
        response.setActualHours(task.getActualHours());
        response.setDueDate(task.getDueDate());
        response.setCreatedAt(task.getCreatedAt());
        response.setUpdatedAt(task.getUpdatedAt());

        if (task.getProject() != null) {
            response.setProjectId(task.getProject().getId());
            response.setProjectTitle(task.getProject().getTitle());
        }

        if (task.getAssignedTo() != null) {
            response.setAssignedTo(mapUserToSummary(task.getAssignedTo()));
        }

        return response;
    }

    @Override
    public Task toEntity(TaskRequest request) {
        if (request == null) {
            return null;
        }

        Task task = new Task();
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setPriority(request.getPriority());
        
        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }
        
        task.setEstimatedHours(request.getEstimatedHours());
        task.setActualHours(request.getActualHours());
        task.setDueDate(request.getDueDate());

        return task;
    }

    @Override
    public void updateEntityFromRequest(TaskRequest request, Task task) {
        if (request == null || task == null) {
            return;
        }

        if (request.getTitle() != null) {
            task.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getPriority() != null) {
            task.setPriority(request.getPriority());
        }
        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }
        if (request.getEstimatedHours() != null) {
            task.setEstimatedHours(request.getEstimatedHours());
        }
        if (request.getActualHours() != null) {
            task.setActualHours(request.getActualHours());
        }
        if (request.getDueDate() != null) {
            task.setDueDate(request.getDueDate());
        }

    }

    private UserSummaryDto mapUserToSummary(User user) {
        if (user == null) {
            return null;
        }

        return new UserSummaryDto(
                user.getId(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getTenantId(),
                user.isActive() ? "ACTIVE" : "INACTIVE",
                0, 
                user.getLastLoginAt()
        );
    }
}
