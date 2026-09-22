package iuh.fit.storageservice.infrastructure.storage;

import iuh.fit.storageservice.application.dto.request.file.FileMoveRequest;
import iuh.fit.storageservice.infrastructure.security.SecurityContextHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageAdapterTest {
    @Mock
    private S3Client s3Client;
    @Mock
    private S3AsyncClient s3AsyncClient;
    @Mock
    private S3Presigner s3Presigner;
    @Mock
    private SecurityContextHelper securityContextHelper;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private StorageAdapter storageAdapter;

    @BeforeEach
    void setUp() {
        storageAdapter = new StorageAdapter(
                s3Client,
                s3AsyncClient,
                s3Presigner,
                securityContextHelper,
                kafkaTemplate
        );
        ReflectionTestUtils.setField(storageAdapter, "bucketName", "goat-sports-bucket");
        ReflectionTestUtils.setField(storageAdapter, "publicDomain", "https://cdn.goatsports.test");
    }

    @Test
    void confirmFileChecksAllObjectsConcurrentlyAndKeepsRequestOrder() {
        CompletableFuture<HeadObjectResponse> firstConfirmation = new CompletableFuture<>();
        when(s3AsyncClient.headObject(any(HeadObjectRequest.class))).thenAnswer(invocation -> {
            HeadObjectRequest request = invocation.getArgument(0);
            if (request.key().endsWith("front.jpg")) {
                return firstConfirmation;
            }
            firstConfirmation.complete(HeadObjectResponse.builder().build());
            return CompletableFuture.completedFuture(HeadObjectResponse.builder().build());
        });

        List<String> confirmedKeys = storageAdapter.confirmFile(List.of(
                new FileMoveRequest("temp/identities/user/front.jpg"),
                new FileMoveRequest("temp/identities/user/back.jpg")
        ));

        assertThat(confirmedKeys).containsExactly(
                "identities/user/front.jpg",
                "identities/user/back.jpg"
        );
    }

    @Test
    void confirmFileRejectsInvalidKeyBeforeCallingR2() {
        List<FileMoveRequest> requests = List.of(
                new FileMoveRequest("temp/identities/user/front.jpg"),
                new FileMoveRequest("identities/user/back.jpg")
        );

        assertThatThrownBy(() -> storageAdapter.confirmFile(requests))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid temporary object key");
        verify(s3AsyncClient, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void deleteByUrlRemovesValidatedPublicClubObject() {
        storageAdapter.deleteByUrl("https://cdn.goatsports.test/clubs/gallery/photo.webp?version=2");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("goat-sports-bucket");
        assertThat(captor.getValue().key()).isEqualTo("clubs/gallery/photo.webp");
    }

    @Test
    void deleteByUrlRejectsForeignStorageDomain() {
        assertThatThrownBy(() -> storageAdapter.deleteByUrl("https://evil.example/clubs/gallery/photo.webp"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("configured public storage domain");
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }
}
