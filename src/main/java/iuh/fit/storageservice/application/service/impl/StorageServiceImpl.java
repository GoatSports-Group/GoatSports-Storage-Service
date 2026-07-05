package iuh.fit.storageservice.application.service.impl;

import iuh.fit.storageservice.application.dto.request.file.UploadRequestCommand;
import iuh.fit.storageservice.application.dto.response.file.DownloadResponseCommand;
import iuh.fit.storageservice.application.dto.response.file.FileResponseCommand;
import iuh.fit.storageservice.application.exception.ServiceException;
import iuh.fit.storageservice.application.port.StoragePort;
import iuh.fit.storageservice.application.service.StorageService;
import iuh.fit.storageservice.application.util.FileUtil;

import java.util.concurrent.CompletableFuture;

public class StorageServiceImpl implements StorageService {

    private final StoragePort storagePort;

    public StorageServiceImpl(StoragePort storagePort) {
        this.storagePort = storagePort;
    }

    public CompletableFuture<FileResponseCommand> upload(UploadRequestCommand command) throws ServiceException {
        FileUtil.assertAllowed(command.originalFileName(), command.content(), command.contentType());

        return storagePort.upload(
                command.content(),
                command.originalFileName(),
                command.contentType(),
                command.folder()
        );
    }

    public String getFileUrl(String key) {
        return storagePort.getFileUrl(key);
    }

    public DownloadResponseCommand download(String key) {
        return storagePort.download(key);
    }

    public void delete(String key) {
        storagePort.delete(key);
    }
}
