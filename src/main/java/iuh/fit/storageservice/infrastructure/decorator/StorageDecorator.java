package iuh.fit.storageservice.infrastructure.decorator;

import iuh.fit.storageservice.application.dto.request.file.FileMoveRequest;
import iuh.fit.storageservice.application.dto.request.file.PresignedUrlRequest;
import iuh.fit.storageservice.application.dto.response.file.DownloadResponseCommand;
import iuh.fit.storageservice.application.dto.response.file.PresignedUrlResponse;
import iuh.fit.storageservice.application.service.StorageService;
import iuh.fit.storageservice.application.service.impl.StorageServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StorageDecorator implements StorageService {
    private final StorageServiceImpl storageServiceImpl;

    @Override
    public List<PresignedUrlResponse> generatePresignedUrl(List<PresignedUrlRequest> request, String userId) {
        return storageServiceImpl.generatePresignedUrl(request, userId);
    }

    @Override
    public List<String> confirmFile(List<FileMoveRequest> requests) {
        return storageServiceImpl.confirmFile(requests);
    }

    @Override
    public void moveFile(List<FileMoveRequest> requests, String correlationId) {
        storageServiceImpl.moveFile(requests, correlationId);
    }

    @Override
    public DownloadResponseCommand download(String key) {
        return storageServiceImpl.download(key);
    }

    @Override
    public void delete(String key) {
        storageServiceImpl.delete(key);
    }

    @Override
    public void deleteByUrl(String publicUrl) {
        storageServiceImpl.deleteByUrl(publicUrl);
    }

    @Override
    public String getFileUrl(String key) {
        return storageServiceImpl.getFileUrl(key);
    }
}
