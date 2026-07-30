package iuh.fit.storageservice.infrastructure.storage;

import iuh.fit.storageservice.application.dto.request.file.FileMoveRequest;
import iuh.fit.storageservice.application.dto.request.file.PresignedUrlRequest;
import iuh.fit.storageservice.application.dto.response.file.DownloadResponseCommand;
import iuh.fit.storageservice.application.dto.response.file.PresignedUrlResponse;
import iuh.fit.storageservice.application.port.StoragePort;
import iuh.fit.storageservice.infrastructure.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import iuh.fit.storageservice.infrastructure.external.kafka.event.FileMoveEvent;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StorageAdapter implements StoragePort {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final SecurityContextHelper securityContextHelper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${cloudflare.r2.bucket}")
    private String bucketName;
    @Value("${cloudflare.r2.public-domain}")
    private String publicDomain;
    @Value("${cloudflare.r2.expiration-minutes}")
    private Long expirationMinutes;
    @Value("${spring.kafka.topic.file-move}")
    private String fileMoveTopic;

    @Override
    public List<PresignedUrlResponse> generatePresignedUrl(List<PresignedUrlRequest> requests, String userId) {
        if (requests == null || requests.isEmpty() || requests.size() > 10) {
            return List.of();
        }

        return requests.stream()
            .map(request -> {
                String key = "temp/%s/%s/%s-%s".formatted(
                    request.folder(),
                    userId,
                    UUID.randomUUID(),
                    request.fileName()
                );

                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(request.contentType())
                    .build();

                PutObjectPresignRequest presignRequest =
                    PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(expirationMinutes))
                        .putObjectRequest(putObjectRequest)
                        .build();

                String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

                return new PresignedUrlResponse(uploadUrl, key);
            })
            .toList();
    }

    @Override
    public List<String> confirmFile(List<FileMoveRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        return requests.stream()
            .map(request -> request.tempKey().replace("temp/", ""))
            .toList();
    }

    @Override
    public void moveFile(List<FileMoveRequest> requests, Long processInstanceKey) {
        // Phát sự kiện di chuyển file lên Kafka và trả về ngay lập tức
        FileMoveEvent event = FileMoveEvent.builder()
            .requests(requests)
            .processInstanceKey(processInstanceKey)
            .build();

        kafkaTemplate.send(fileMoveTopic, event);
        log.info("Published FileMoveEvent to topic {} for process {} with {} files", fileMoveTopic, processInstanceKey, requests.size());
    }

    @Override
    public DownloadResponseCommand download(String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        ResponseInputStream<GetObjectResponse> s = s3Client.getObject(request);

        return new DownloadResponseCommand(s, s.response().contentType(), s.response().contentLength());
    }

    @Override
    public void delete(String key) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete file with key: " + key, e);
        }
    }

    @Override
    public String getFileUrl(String key) {
        if (isPublic(key)) {
            return generatePublicUrl(key);
        }

        return generatePresignedUrl(key);
    }

    private String generatePublicUrl(String key) {
        return String.format(
                "%s/%s",
                publicDomain,
                key
        );
    }

    private String generatePresignedUrl(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(expirationMinutes))
                        .getObjectRequest(getObjectRequest)
                        .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    private boolean isPublic(String key) {
        return key.contains("venues/") || key.contains("avatars/");
    }
}
