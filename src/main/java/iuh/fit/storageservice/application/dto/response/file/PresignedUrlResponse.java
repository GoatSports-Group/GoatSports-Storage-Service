package iuh.fit.storageservice.application.dto.response.file;

public record PresignedUrlResponse(String uploadUrl, String objectKey) {
}
