package iuh.fit.storageservice.application.service.impl;

import iuh.fit.storageservice.application.dto.request.file.FileMoveRequest;
import iuh.fit.storageservice.application.dto.request.file.PresignedUrlRequest;
import iuh.fit.storageservice.application.dto.response.file.DownloadResponseCommand;
import iuh.fit.storageservice.application.dto.response.file.PresignedUrlResponse;
import iuh.fit.storageservice.application.port.StoragePort;
import iuh.fit.storageservice.application.service.StorageService;

import java.util.List;

public class StorageServiceImpl implements StorageService {

    private final StoragePort storagePort;

    public StorageServiceImpl(StoragePort storagePort) {
        this.storagePort = storagePort;
    }

    @Override
    public List<PresignedUrlResponse> generatePresignedUrl(List<PresignedUrlRequest> requests) {
        return storagePort.generatePresignedUrl(requests);
    }

    @Override
    public List<String> confirmFile(List<FileMoveRequest> requests) {
        return storagePort.confirmFile(requests);
    }

    @Override
    public void moveFile(List<FileMoveRequest> requests, Long processInstanceKey) {
        storagePort.moveFile(requests, processInstanceKey);
    }

    @Override
    public DownloadResponseCommand download(String key) {
        return storagePort.download(key);
    }

    @Override
    public void delete(String key) {
        storagePort.delete(key);
    }

    @Override
    public String getFileUrl(String key) {
        return storagePort.getFileUrl(key);
    }
}
