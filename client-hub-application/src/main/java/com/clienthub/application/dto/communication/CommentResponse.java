package com.clienthub.application.dto.communication;

import com.clienthub.application.dto.UserSummaryDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CommentResponse {
    private Long id;
    private String content;
    private UserSummaryDto author;

    private Long threadId; // Reference to container thread
    private boolean isDeleted;
    private List<String> attachmentUrls = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;

    public CommentResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public UserSummaryDto getAuthor() { return author; }
    public void setAuthor(UserSummaryDto author) { this.author = author; }

    public Long getThreadId() { return threadId; }
    public void setThreadId(Long threadId) { this.threadId = threadId; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public List<String> getAttachmentUrls() { return attachmentUrls; }
    public void setAttachmentUrls(List<String> attachmentUrls) { this.attachmentUrls = attachmentUrls; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}