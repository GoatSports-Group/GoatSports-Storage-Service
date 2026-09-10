package iuh.fit.storageservice.application.dto.request.file;

import java.util.List;

public record MoveFileRequest(
        List<FileMoveRequest> requests,
        String correlationId
) {}