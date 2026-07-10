package iuh.fit.storageservice.presentation.controller.internal;

import iuh.fit.storageservice.application.dto.request.file.FileMoveRequest;
import iuh.fit.storageservice.application.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping( "/internal/files")
@RequiredArgsConstructor
public class StorageInternalController {
    private final StorageService storageService;

    @PostMapping("/confirm")
    public ResponseEntity<List<String>> confirmFile(@RequestBody List<FileMoveRequest> requests) {
        return ResponseEntity.status(HttpStatus.OK).body(storageService.confirmAndMoveFile(requests));
    }
}
