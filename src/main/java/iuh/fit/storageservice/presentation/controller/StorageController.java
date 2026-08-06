package iuh.fit.storageservice.presentation.controller;

import iuh.fit.shared.annotation.AuditLog;
import iuh.fit.storageservice.application.dto.request.file.PresignedUrlRequest;
import iuh.fit.storageservice.application.dto.response.file.DownloadResponseCommand;
import iuh.fit.storageservice.application.dto.response.file.PresignedUrlResponse;
import iuh.fit.storageservice.application.exception.ServiceException;
import iuh.fit.storageservice.application.service.StorageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class StorageController {
    private final StorageService storageService;

    @GetMapping("/download")
    @AuditLog(action = "DOWNLOAD_FILE", description = "Tải tài liệu #{#key}")
    public ResponseEntity<InputStreamResource> downloadFile(@RequestParam("key") String key) throws ServiceException {
        if(key == null || key.isEmpty()) {
            throw new ServiceException("Đường dẫn không được để trống");
        }

        DownloadResponseCommand response = storageService.download(key);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"file.pdf\"")
                .body(new InputStreamResource(response.stream()));
    }

    @DeleteMapping
    @AuditLog(action = "DELETE_FILE", description = "Xóa tài liệu #{#key}")
    public ResponseEntity<Void> deleteFile(@RequestParam("key") String key) throws ServiceException {
        if(key == null || key.isEmpty()) {
            throw new ServiceException("Đường dẫn không được để trống");
        }
        storageService.delete(key);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping
    public ResponseEntity<String> getFileUrl(@RequestParam("key") String key) throws ServiceException {
        if(key == null || key.isEmpty()) {
            throw new ServiceException("Đường dẫn không được để trống");
        }

        String response = storageService.getFileUrl(key);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/presigned-url")
    @AuditLog(action = "GET_PRESIGNED", description = "Lấy đường dẫn lưu tài liệu tạm thời")
    public ResponseEntity<List<PresignedUrlResponse>> getPresignedUrl(
            @RequestBody List<PresignedUrlRequest> requests,
            @RequestHeader("X-User-Id") String userId
    ) {
        return ResponseEntity.status(HttpStatus.OK).body(storageService.generatePresignedUrl(requests, userId));
    }
}
