package iuh.fit.storageservice.application.dto.request.file;

public record UploadRequestCommand(
        byte[] content,
        String originalFileName,
        String contentType,
        String folder
) {}
