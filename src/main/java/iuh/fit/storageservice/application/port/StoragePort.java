package iuh.fit.storageservice.application.port;

import iuh.fit.storageservice.application.dto.request.file.FileMoveRequest;
import iuh.fit.storageservice.application.dto.request.file.PresignedUrlRequest;
import iuh.fit.storageservice.application.dto.response.file.DownloadResponseCommand;
import iuh.fit.storageservice.application.dto.response.file.PresignedUrlResponse;

import java.util.List;

public interface StoragePort {
    List<PresignedUrlResponse> generatePresignedUrl(List<PresignedUrlRequest> requests);

    List<String> confirmAndMoveFile(List<FileMoveRequest> requests);

    DownloadResponseCommand download(String key);

    void delete(String key);

    String getFileUrl(String key);
}
