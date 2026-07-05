package iuh.fit.storageservice.infrastructure.storage;

import iuh.fit.storageservice.application.dto.response.file.DownloadResponseCommand;
import iuh.fit.storageservice.application.dto.response.file.FileResponseCommand;
import iuh.fit.storageservice.application.port.StoragePort;
import iuh.fit.storageservice.infrastructure.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class StorageAdapter implements StoragePort {
    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final SecurityContextHelper securityContextHelper;

    @Value("${cloudflare.r2.bucket}")
    private String bucketName;
    @Value("${cloudflare.r2.public-domain}")
    private String publicDomain;
    @Value("${cloudflare.r2.expiration-minutes}")
    private Long expirationMinutes;

    @Override
    public CompletableFuture<FileResponseCommand> upload(
            byte[] content, String originalFileName, String contentType, String folder
    ) {
        String userId = securityContextHelper.getCurrentUserId().orElse("anonymous");
        String extension = FilenameUtils.getExtension(originalFileName);
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String key = String.format(
                "%s/%s/%s/%s.%s",
                userId,
                folder,
                datePath,
                UUID.randomUUID(),
                extension
        );

        PutObjectRequest request = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(contentType)
                        .build();

        s3Client.putObject(
                request,
                RequestBody.fromBytes(content)
        );

        return CompletableFuture.completedFuture(
                new FileResponseCommand(key)
        );
    }

    @Override
    public String getFileUrl(String key) {
        if (isPublic(key)) {
            return generatePublicUrl(key);
        }

        return generatePresignedUrl(key);
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

        return presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }

    private boolean isPublic(String key) {
        return key.contains("/venues/");
    }
}
