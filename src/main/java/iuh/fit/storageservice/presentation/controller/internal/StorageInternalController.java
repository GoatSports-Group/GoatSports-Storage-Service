package iuh.fit.storageservice.presentation.controller.internal;

import iuh.fit.shared.annotation.AuditLog;
import iuh.fit.storageservice.application.dto.request.file.FileMoveRequest;
import iuh.fit.storageservice.application.dto.request.file.MoveFileRequest;
import iuh.fit.storageservice.application.dto.request.file.PresignedUrlRequest;
import iuh.fit.storageservice.application.dto.response.file.PresignedUrlResponse;
import iuh.fit.storageservice.application.exception.ServiceException;
import iuh.fit.storageservice.application.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping( "/internal/files")
@RequiredArgsConstructor
public class StorageInternalController {
    private final StorageService storageService;

    @PostMapping("/presigned-url")
    @AuditLog(action = "GET_PRESIGNED", description = "Lấy đường dẫn lưu tài liệu tạm thời")
    public ResponseEntity<List<PresignedUrlResponse>> getPresignedUrl(
            @RequestBody List<PresignedUrlRequest> requests,
            @RequestParam String userId
    ) {
        return ResponseEntity.status(HttpStatus.OK).body(storageService.generatePresignedUrl(requests, userId));
    }

    @PostMapping("/confirm")
    @AuditLog(action = "CONFIRM_FILE", description = "Xác nhận đường dẫn lưu tài liệu")
    public ResponseEntity<List<String>> confirmFile(@RequestBody List<FileMoveRequest> requests) {
        return ResponseEntity.status(HttpStatus.OK).body(storageService.confirmFile(requests));
    }

    @PostMapping("/move")
    @AuditLog(action = "MOVE_FILE", description = "Di chuyển đường dẫn lưu tài liệu")
    public ResponseEntity<Void> moveFile(@RequestBody MoveFileRequest request) {
        storageService.moveFile(request.requests(), request.processInstanceKey());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<String> getFileUrl(@RequestParam("key") String key) throws ServiceException {
        if(key == null || key.isEmpty()) {
            throw new ServiceException("Đường dẫn không được để trống");
        }

        String response = storageService.getFileUrl(key);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
