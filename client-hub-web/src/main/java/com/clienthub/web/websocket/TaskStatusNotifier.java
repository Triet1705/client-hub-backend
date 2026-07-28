package com.clienthub.web.websocket;

import com.clienthub.domain.event.TaskChangedEvent;
import com.clienthub.domain.enums.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class TaskStatusNotifier {

    private static final Logger log = LoggerFactory.getLogger(TaskStatusNotifier.class);

    private final SimpMessagingTemplate messagingTemplate;

    public TaskStatusNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public record TaskChangedMessage(
            UUID id,
            UUID projectId,
            TaskStatus status,
            String changeType,
            Instant updatedAt) {
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTaskChanged(TaskChangedEvent event) {
        TaskChangedMessage message = new TaskChangedMessage(
                event.getTaskId(),
                event.getProjectId(),
                event.getStatus(),
                event.getChangeType(),
                event.getChangedAt()
        );

        messagingTemplate.convertAndSend(
                "/topic/projects/" + event.getProjectId() + "/tasks",
                message
        );
        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(event.getProjectOwnerId());
        if (event.getAssigneeId() != null) {
            recipients.add(event.getAssigneeId());
        }
        if (event.getPreviousAssigneeId() != null) {
            recipients.add(event.getPreviousAssigneeId());
        }
        for (UUID recipientId : recipients) {
            messagingTemplate.convertAndSend(
                    "/topic/users/" + recipientId + "/tasks",
                    message
            );
        }
        log.debug("Sent task update for task {}", event.getTaskId());
    }
}
