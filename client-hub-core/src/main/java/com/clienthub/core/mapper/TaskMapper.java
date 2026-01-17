package com.clienthub.core.mapper;

import com.clienthub.core.domain.entity.Task;
import com.clienthub.core.dto.task.TaskRequest;
import com.clienthub.core.dto.task.TaskResponse;

public interface TaskMapper {

    TaskResponse toResponse(Task task);

    Task toEntity(TaskRequest request);

    void updateEntityFromRequest(TaskRequest request, Task task);
}
