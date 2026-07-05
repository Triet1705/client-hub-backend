package com.clienthub.application.dto.project;

import java.time.Instant;

public class ProjectFileResponse {
    private String fileUrl;
    private String fileName;
    private String sourceType;
    private String sourceId;
    private Long commentId;
    private String authorName;
    private Instant createdAt;

    public ProjectFileResponse() {
    }

    public ProjectFileResponse(String fileUrl, String fileName, String sourceType, String sourceId,
                               Long commentId, String authorName, Instant createdAt) {
        this.fileUrl = fileUrl;
        this.fileName = fileName;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.commentId = commentId;
        this.authorName = authorName;
        this.createdAt = createdAt;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public Long getCommentId() {
        return commentId;
    }

    public void setCommentId(Long commentId) {
        this.commentId = commentId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
