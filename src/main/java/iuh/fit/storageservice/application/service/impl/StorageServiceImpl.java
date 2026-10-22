package iuh.fit.storageservice.application.service.impl;

import iuh.fit.storageservice.application.dto.request.file.FileMoveRequest;
import iuh.fit.storageservice.application.dto.request.file.FileRequester;
import iuh.fit.storageservice.application.dto.request.file.PresignedUrlRequest;
import iuh.fit.storageservice.application.dto.response.file.DownloadResponseCommand;
import iuh.fit.storageservice.application.dto.response.file.PresignedUrlResponse;
import iuh.fit.storageservice.application.exception.ForbiddenException;
import iuh.fit.storageservice.application.exception.ServiceException;
import iuh.fit.storageservice.application.port.ChatAttachmentAccessPort;
import iuh.fit.storageservice.application.port.StoragePort;
import iuh.fit.storageservice.application.service.FileAccessPolicy;
import iuh.fit.storageservice.application.service.StorageService;
import iuh.fit.storageservice.application.service.UploadPolicy;

import java.util.List;

public class StorageServiceImpl implements StorageService {

    private final StoragePort storagePort;
    private final ChatAttachmentAccessPort chatAttachmentAccessPort;

    public StorageServiceImpl(StoragePort storagePort, ChatAttachmentAccessPort chatAttachmentAccessPort) {
        this.storagePort = storagePort;
        this.chatAttachmentAccessPort = chatAttachmentAccessPort;
    }

    @Override
    public List<PresignedUrlResponse> generatePresignedUrl(List<PresignedUrlRequest> requests, String userId) {
        UploadPolicy.validate(requests);
        return storagePort.generatePresignedUrl(requests, userId);
    }

    @Override
    public List<String> confirmFile(List<FileMoveRequest> requests) {
        return storagePort.confirmFile(requests);
    }

    @Override
    public void moveFile(List<FileMoveRequest> requests, String correlationId) {
        storagePort.moveFile(requests, correlationId);
    }

    @Override
    public DownloadResponseCommand download(String key, FileRequester requester) throws ServiceException {
        requireReadable(key, requester);
        return storagePort.download(key);
    }

    @Override
    public void delete(String key) {
        storagePort.delete(key);
    }

    @Override
    public void delete(String key, FileRequester requester) throws ServiceException {
        if (!FileAccessPolicy.canDelete(key, requester)) {
            throw new ForbiddenException("Bạn chỉ được xóa tệp của chính mình.");
        }
        storagePort.delete(key);
    }

    @Override
    public void deleteByUrl(String publicUrl) {
        storagePort.deleteByUrl(publicUrl);
    }

    @Override
    public String getFileUrl(String key) {
        return storagePort.getFileUrl(key);
    }

    @Override
    public String getFileUrl(String key, FileRequester requester) throws ServiceException {
        requireReadable(key, requester);
        return storagePort.getFileUrl(key);
    }

    private void requireReadable(String key, FileRequester requester) throws ServiceException {
        boolean allowed = switch (FileAccessPolicy.read(key, requester)) {
            case ALLOW -> true;
            case CHECK_CHAT_MEMBERSHIP -> chatAttachmentAccessPort.canView(requester.userId(), key);
            case DENY -> false;
        };
        if (!allowed) {
            throw new ForbiddenException("Bạn không có quyền xem tệp này.");
        }
    }
}
