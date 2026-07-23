package com.clienthub.application.dto;

import java.nio.file.Path;

public record AttachmentDownload(
        Path path,
        String originalFilename,
        String mediaType,
        long sizeBytes) {
}
