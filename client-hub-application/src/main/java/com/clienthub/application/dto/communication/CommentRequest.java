package com.clienthub.application.dto.communication;

import com.clienthub.domain.enums.CommentTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CommentRequest {

    @NotNull(message = "Target type is required (PROJECT, TASK, INVOICE)")
    private CommentTargetType targetType;

    @NotBlank(message = "Target ID is required")
    private String targetId;

    @NotBlank(message = "Content cannot be empty")
    @Size(max = 5000, message = "Comment content must not exceed 5000 characters")
    private String content;

    private List<String> attachmentUrls;

    public CommentTargetType getTargetType() { return targetType; }
    public void setTargetType(CommentTargetType targetType) { this.targetType = targetType; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<String> getAttachmentUrls() { return attachmentUrls; }
    public void setAttachmentUrls(List<String> attachmentUrls) { this.attachmentUrls = attachmentUrls; }
}