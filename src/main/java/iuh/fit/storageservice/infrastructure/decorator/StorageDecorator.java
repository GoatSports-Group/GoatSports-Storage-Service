package iuh.fit.storageservice.infrastructure.decorator;

import iuh.fit.storageservice.application.dto.request.file.UploadRequestCommand;
import iuh.fit.storageservice.application.dto.response.file.DownloadResponseCommand;
import iuh.fit.storageservice.application.dto.response.file.FileResponseCommand;
import iuh.fit.storageservice.application.exception.ServiceException;
import iuh.fit.storageservice.application.service.StorageService;
import iuh.fit.storageservice.application.service.impl.StorageServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class StorageDecorator implements StorageService {
    private final StorageServiceImpl storageServiceImpl;

    @Override
    public CompletableFuture<FileResponseCommand> upload(UploadRequestCommand command) throws ServiceException {
        return storageServiceImpl.upload(command);
    }

    @Override
    public String getFileUrl(String key) {
        return storageServiceImpl.getFileUrl(key);
    }

    @Override
    public DownloadResponseCommand download(String key) {
        return storageServiceImpl.download(key);
    }

    @Override
    public void delete(String key) {
        storageServiceImpl.delete(key);
    }
}
