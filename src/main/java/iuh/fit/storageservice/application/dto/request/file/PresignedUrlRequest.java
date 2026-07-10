package iuh.fit.storageservice.application.dto.request.file;

public record PresignedUrlRequest(
        String fileName,
        String contentType,
        String folder
) {}
