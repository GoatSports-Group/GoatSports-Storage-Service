package iuh.fit.storageservice.application.service;

import iuh.fit.storageservice.application.dto.request.file.FileMoveRequest;
import iuh.fit.storageservice.application.dto.request.file.FileRequester;
import iuh.fit.storageservice.application.dto.request.file.PresignedUrlRequest;
import iuh.fit.storageservice.application.dto.response.file.DownloadResponseCommand;
import iuh.fit.storageservice.application.dto.response.file.PresignedUrlResponse;
import iuh.fit.storageservice.application.exception.ServiceException;

import java.util.List;

public interface StorageService {
    List<PresignedUrlResponse> generatePresignedUrl(List<PresignedUrlRequest> requests, String userId);

    List<String> confirmFile(List<FileMoveRequest> requests);

    void moveFile(List<FileMoveRequest> requests, String correlationId);

    DownloadResponseCommand download(String key, FileRequester requester) throws ServiceException;

    /** Dọn tệp hộ service khác (/internal); không kiểm chủ tệp. */
    void delete(String key);

    /** Người dùng tự xóa tệp của mình. */
    void delete(String key, FileRequester requester) throws ServiceException;

    void deleteByUrl(String publicUrl);

    /** Service khác hỏi qua /internal; không kiểm người xem. */
    String getFileUrl(String key);

    String getFileUrl(String key, FileRequester requester) throws ServiceException;
}
