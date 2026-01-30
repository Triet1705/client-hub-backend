package com.clienthub.application.mapper;

import com.clienthub.domain.entity.Task;
import com.clienthub.application.dto.task.TaskRequest;
import com.clienthub.application.dto.task.TaskResponse;

public interface TaskMapper {

    TaskResponse toResponse(Task task);

    Task toEntity(TaskRequest request);

    void updateEntityFromRequest(TaskRequest request, Task task);
}
