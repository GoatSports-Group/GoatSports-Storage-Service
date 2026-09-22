package iuh.fit.storageservice.infrastructure.storage;

import iuh.fit.storageservice.application.dto.request.file.FileMoveRequest;
import iuh.fit.storageservice.application.dto.request.file.PresignedUrlRequest;
import iuh.fit.storageservice.application.dto.response.file.DownloadResponseCommand;
import iuh.fit.storageservice.application.dto.response.file.PresignedUrlResponse;
import iuh.fit.storageservice.application.port.StoragePort;
import iuh.fit.storageservice.infrastructure.external.kafka.event.FileMoveEvent;
import iuh.fit.storageservice.infrastructure.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class StorageAdapter implements StoragePort {
    private final S3Client s3Client;
    private final S3AsyncClient s3AsyncClient;
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
        if (requests.stream().anyMatch(request -> request.contentLength() != null
                && request.contentLength() <= 0)) {
            return List.of();
        }

        long startedAt = System.nanoTime();
        List<PresignedUrlResponse> responses = requests.parallelStream()
            .map(request -> {
                String key = "temp/%s/%s/%s-%s".formatted(
                    request.folder(),
                    userId,
                    UUID.randomUUID(),
                    request.fileName()
                );

                PutObjectRequest.Builder putObjectRequestBuilder = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(request.contentType());
                if (request.contentLength() != null) {
                    putObjectRequestBuilder.contentLength(request.contentLength());
                }
                PutObjectRequest putObjectRequest = putObjectRequestBuilder.build();

                PutObjectPresignRequest presignRequest =
                    PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(expirationMinutes))
                        .putObjectRequest(putObjectRequest)
                        .build();

                String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

                return new PresignedUrlResponse(uploadUrl, key);
            })
            .toList();
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        log.debug("Generated {} R2 presigned upload URLs in {} ms", responses.size(), elapsedMillis);
        return responses;
    }

    @Override
    public List<String> confirmFile(List<FileMoveRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        List<String> tempKeys = requests.stream()
                .map(FileMoveRequest::tempKey)
                .map(this::validatedTemporaryObjectKey)
                .toList();
        List<CompletableFuture<HeadObjectResponse>> confirmations = tempKeys.stream()
                .map(tempKey -> s3AsyncClient.headObject(HeadObjectRequest.builder()
                        .bucket(bucketName)
                        .key(tempKey)
                        .build()))
                .toList();

        long startedAt = System.nanoTime();
        awaitConfirmations(confirmations);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        log.info("Confirmed {} temporary objects on R2 in {} ms", tempKeys.size(), elapsedMillis);
        return tempKeys.stream()
                .map(tempKey -> tempKey.substring("temp/".length()))
                .toList();
    }

    @Override
    public void moveFile(List<FileMoveRequest> requests, String correlationId) {
        // Phát sự kiện di chuyển file lên Kafka và trả về ngay lập tức
        FileMoveEvent event = FileMoveEvent.builder()
            .requests(requests)
            .correlationId(correlationId)
            .build();

        kafkaTemplate.send(fileMoveTopic, correlationId, event).join();
        log.info("Published FileMoveEvent to topic {} for correlation {} with {} files", fileMoveTopic, correlationId, requests.size());
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
    public void deleteByUrl(String publicUrl) {
        String domain = publicDomain == null ? "" : publicDomain.replaceAll("/+$", "");
        String value = publicUrl == null ? "" : publicUrl.trim();
        String prefix = domain + "/";
        if (domain.isBlank() || !value.startsWith(prefix)) {
            throw new IllegalArgumentException("File URL does not belong to the configured public storage domain");
        }
        String key = value.substring(prefix.length());
        int queryIndex = key.indexOf('?');
        if (queryIndex >= 0) key = key.substring(0, queryIndex);
        if (key.isBlank() || key.contains("..") || key.startsWith("temp/") || !isPublic(key)) {
            throw new IllegalArgumentException("Invalid public storage URL");
        }
        delete(key);
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
        return key.contains("venues/") || key.contains("avatars/")
                ||  key.contains("clubs/") ||  key.contains("social-posts/");
    }

    private String validatedTemporaryObjectKey(String tempKey) {
        if (tempKey == null || !tempKey.startsWith("temp/")) {
            throw new IllegalArgumentException("Invalid temporary object key");
        }
        return tempKey;
    }

    private void awaitConfirmations(List<CompletableFuture<HeadObjectResponse>> confirmations) {
        try {
            CompletableFuture.allOf(confirmations.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw exception;
        }
    }
}
