package com.clienthub.application.dto;

import java.time.Instant;
import java.util.UUID;

public class AttachmentResponseDto {
    private UUID id;
    private String fileUrl;
    private String fileName;
    private String fileType;
    private long sizeBytes;
    private Instant uploadedAt;

    public AttachmentResponseDto() {
    }

    public AttachmentResponseDto(UUID id, String fileUrl, String fileName, String fileType,
                                 long sizeBytes, Instant uploadedAt) {
        this.id = id;
        this.fileUrl = fileUrl;
        this.fileName = fileName;
        this.fileType = fileType;
        this.sizeBytes = sizeBytes;
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

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
