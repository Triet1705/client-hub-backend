package com.clienthub.infrastructure.storage;

import com.clienthub.common.context.TenantContext;
import com.clienthub.infrastructure.config.MinioConfig;
import com.clienthub.infrastructure.exception.FileStorageException;
import io.minio.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class MinioStorageService implements FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(MinioStorageService.class);

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    public MinioStorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public String uploadFile(MultipartFile file, String folder) {
        String tenantId = TenantContext.getTenantId();
        if (!StringUtils.hasText(tenantId)) {
            throw new FileStorageException("Tenant Context missing. Cannot upload file safely.");
        }

        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }

            String originalFilename = file.getOriginalFilename();
            String sanitizedFilename = originalFilename != null ? originalFilename.replaceAll("\\s+", "_") : "unknown";
            String objectName = String.format("%s/%s/%s-%s",
                    tenantId,
                    folder,
                    UUID.randomUUID(),
                    sanitizedFilename
            );

            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .stream(inputStream, file.getSize(), -1)
                                .contentType(file.getContentType())
                                .build());
            }

            logger.debug("Uploaded file to MinIO: {}", objectName);
            return objectName;

        } catch (Exception e) {
            logger.error("MinIO Upload Error", e);
            throw new FileStorageException("Could not upload file: " + e.getMessage());
        }
    }

    @Override
    public String getFileUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(15, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            logger.error("MinIO URL Generation Error", e);
            throw new FileStorageException("Could not get file URL: " + e.getMessage());
        }
    }

    @Override
    public void deleteFile(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
            logger.debug("Deleted file from MinIO: {}", objectName);
        } catch (Exception e) {
            logger.error("MinIO Delete Error", e);
            throw new FileStorageException("Could not delete file: " + e.getMessage());
        }
    }
}