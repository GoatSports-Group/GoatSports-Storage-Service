package iuh.fit.storageservice.infrastructure.external.kafka.consumer;

import iuh.fit.storageservice.application.dto.request.file.FileMoveRequest;
import iuh.fit.storageservice.application.port.StoragePort;
import iuh.fit.storageservice.infrastructure.external.kafka.event.FileMoveCompletedEvent;
import iuh.fit.storageservice.infrastructure.external.kafka.event.FileMoveEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedCopy;
import software.amazon.awssdk.transfer.s3.model.Copy;
import software.amazon.awssdk.transfer.s3.model.CopyRequest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileMoveConsumerTest {
    private static final String BUCKET_NAME = "goatsports";
    private static final String COMPLETED_TOPIC = "file-move-completed-topic";
    private static final String TEMP_KEY = "temp/venues/user-id/venue.jpg";
    private static final String DESTINATION_KEY = "venues/user-id/venue.jpg";

    @Mock
    private S3TransferManager transferManager;
    @Mock
    private S3Client s3Client;
    @Mock
    private StoragePort storagePort;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private FileMoveConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new FileMoveConsumer(transferManager, s3Client, storagePort, kafkaTemplate);
        ReflectionTestUtils.setField(consumer, "bucketName", BUCKET_NAME);
        ReflectionTestUtils.setField(consumer, "fileMoveCompletedTopic", COMPLETED_TOPIC);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void shouldTreatExistingDestinationAsSuccessfulReplay() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenAnswer(invocation -> {
            HeadObjectRequest request = invocation.getArgument(0);
            if (DESTINATION_KEY.equals(request.key())) {
                return HeadObjectResponse.builder().build();
            }
            throw notFound();
        });

        consumer.consume(event());

        verify(transferManager, never()).copy(any(CopyRequest.class));
        verify(storagePort, never()).delete(anyString());
        verify(kafkaTemplate).send(
                COMPLETED_TOPIC,
                "owner-application:application-id",
                FileMoveCompletedEvent.builder()
                        .correlationId("owner-application:application-id")
                        .success(true)
                        .build()
        );
    }

    @Test
    void shouldRecoverWhenAnotherConsumerMovesSourceDuringCopy() {
        AtomicInteger destinationLookups = new AtomicInteger();
        AtomicInteger sourceLookups = new AtomicInteger();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenAnswer(invocation -> {
            HeadObjectRequest request = invocation.getArgument(0);
            if (DESTINATION_KEY.equals(request.key())) {
                if (destinationLookups.incrementAndGet() == 1) {
                    throw notFound();
                }
                return HeadObjectResponse.builder().build();
            }
            if (TEMP_KEY.equals(request.key()) && sourceLookups.incrementAndGet() == 1) {
                return HeadObjectResponse.builder().build();
            }
            throw notFound();
        });
        Copy copy = org.mockito.Mockito.mock(Copy.class);
        CompletableFuture<CompletedCopy> failedCopy = CompletableFuture.failedFuture(notFound());
        when(copy.completionFuture()).thenReturn(failedCopy);
        when(transferManager.copy(any(CopyRequest.class))).thenReturn(copy);

        consumer.consume(event());

        verify(storagePort, never()).delete(anyString());
        verify(kafkaTemplate).send(
                COMPLETED_TOPIC,
                "owner-application:application-id",
                FileMoveCompletedEvent.builder()
                        .correlationId("owner-application:application-id")
                        .success(true)
                        .build()
        );
    }

    private FileMoveEvent event() {
        return FileMoveEvent.builder()
                .requests(List.of(new FileMoveRequest(TEMP_KEY)))
                .correlationId("owner-application:application-id")
                .build();
    }

    private NoSuchKeyException notFound() {
        return NoSuchKeyException.builder()
                .statusCode(404)
                .message("Object does not exist")
                .build();
    }
}
