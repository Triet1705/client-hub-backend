package com.clienthub.application.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class AttachmentResponseDto {
    private UUID id;
    private String fileUrl;
    private String fileName;
    private String fileType;
    private LocalDateTime uploadedAt;

    public AttachmentResponseDto() {
    }

    public AttachmentResponseDto(UUID id, String fileUrl, String fileName, String fileType, LocalDateTime uploadedAt) {
        this.id = id;
        this.fileUrl = fileUrl;
        this.fileName = fileName;
        this.fileType = fileType;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
