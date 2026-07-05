package iuh.fit.storageservice.application.service;

import iuh.fit.storageservice.application.dto.request.file.UploadRequestCommand;
import iuh.fit.storageservice.application.dto.response.file.DownloadResponseCommand;
import iuh.fit.storageservice.application.dto.response.file.FileResponseCommand;
import iuh.fit.storageservice.application.exception.ServiceException;

import java.util.concurrent.CompletableFuture;

public interface StorageService {
    CompletableFuture<FileResponseCommand> upload(UploadRequestCommand command) throws ServiceException;

    String getFileUrl(String key);

    DownloadResponseCommand download(String key);

    void delete(String key);
}
