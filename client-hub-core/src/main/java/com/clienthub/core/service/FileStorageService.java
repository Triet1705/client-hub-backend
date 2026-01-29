package com.clienthub.core.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;


public interface FileStorageService {
    String uploadFile(MultipartFile file, String folder);
    String getFileUrl(String fileName);

    void deleteFile(String fileName);
}
