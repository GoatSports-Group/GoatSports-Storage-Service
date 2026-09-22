package iuh.fit.storageservice.application.service;

import iuh.fit.storageservice.application.dto.request.file.FileMoveRequest;
import iuh.fit.storageservice.application.dto.request.file.PresignedUrlRequest;
import iuh.fit.storageservice.application.dto.response.file.DownloadResponseCommand;
import iuh.fit.storageservice.application.dto.response.file.PresignedUrlResponse;

import java.util.List;

public interface StorageService {
    List<PresignedUrlResponse> generatePresignedUrl(List<PresignedUrlRequest> requests, String userId);

    List<String> confirmFile(List<FileMoveRequest> requests);

    void moveFile(List<FileMoveRequest> requests, String correlationId);

    DownloadResponseCommand download(String key);

    void delete(String key);

    void deleteByUrl(String publicUrl);

    String getFileUrl(String key);
}
