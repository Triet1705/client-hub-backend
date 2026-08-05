package com.clienthub.web.websocket;

import com.clienthub.domain.enums.TaskStatus;
import com.clienthub.domain.event.TaskChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TaskStatusNotifierTest {

    @Test
    void taskChangePublishesProjectAndAssigneeMessages() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        TaskStatusNotifier notifier = new TaskStatusNotifier(messagingTemplate);
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        String tenantId = "default";
        UUID ownerId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();

        notifier.handleTaskChanged(new TaskChangedEvent(
                this,
                taskId,
                projectId,
                tenantId,
                ownerId,
                assigneeId,
                null,
                TaskStatus.IN_PROGRESS,
                "STATUS_CHANGED"));

        verify(messagingTemplate).convertAndSend(
                (String) eq("/topic/projects/" + projectId + "/tasks"),
                (Object) argThat(message -> message instanceof TaskStatusNotifier.TaskChangedMessage changed
                        && changed.id().equals(taskId)
                        && changed.status() == TaskStatus.IN_PROGRESS));
        verify(messagingTemplate).convertAndSend(
                (String) eq("/topic/tenants/" + tenantId + "/tasks"),
                (Object) argThat(message -> message instanceof TaskStatusNotifier.TaskChangedMessage changed
                        && changed.id().equals(taskId)));
        verify(messagingTemplate).convertAndSend(
                (String) eq("/topic/users/" + assigneeId + "/tasks"),
                (Object) argThat(message -> message instanceof TaskStatusNotifier.TaskChangedMessage changed
                        && changed.id().equals(taskId)));
        verify(messagingTemplate).convertAndSend(
                (String) eq("/topic/users/" + ownerId + "/tasks"),
                (Object) argThat(message -> message instanceof TaskStatusNotifier.TaskChangedMessage changed
                        && changed.id().equals(taskId)));
    }
}
