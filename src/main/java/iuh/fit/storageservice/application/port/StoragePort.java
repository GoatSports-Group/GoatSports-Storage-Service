package iuh.fit.storageservice.application.port;

import iuh.fit.storageservice.application.dto.response.file.DownloadResponseCommand;
import iuh.fit.storageservice.application.dto.response.file.FileResponseCommand;

import java.util.concurrent.CompletableFuture;

public interface StoragePort {
    CompletableFuture<FileResponseCommand> upload(
            byte[] content,
            String originalFileName,
            String contentType,
            String folder
    );

    String getFileUrl(String key);

    DownloadResponseCommand download(String key);

    void delete(String key);
}
