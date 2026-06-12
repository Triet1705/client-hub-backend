package com.clienthub.application.service;

import com.clienthub.application.dto.AttachmentResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Arrays;
import java.util.List;

@Service
public class AttachmentService {

    @Value("${file.upload-dir:./uploads/attachments}")
    private String uploadDir;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final List<String> ALLOWED_CONTENT_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "application/pdf", 
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    public AttachmentResponseDto uploadDocument(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Failed to store empty file.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds limit of 5MB.");
        }

        String contentType = file.getContentType();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("File type not allowed.");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown");
        if (originalFilename.contains("..")) {
            throw new IllegalArgumentException("Sorry! Filename contains invalid path sequence " + originalFilename);
        }

        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            UUID fileId = UUID.randomUUID();
            String extension = originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
            String generatedFileName = fileId.toString() + extension;
            Path destinationFile = uploadPath.resolve(generatedFileName).normalize().toAbsolutePath();
            if (!destinationFile.getParent().equals(uploadPath.toAbsolutePath())) {
                throw new SecurityException("Cannot store file outside current directory.");
            }

            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = "/uploads/attachments/" + generatedFileName;

            return new AttachmentResponseDto(fileId, fileUrl, originalFilename, contentType, LocalDateTime.now());

        } catch (IOException e) {
            throw new RuntimeException("Failed to store file " + originalFilename, e);
        }
    }
}
