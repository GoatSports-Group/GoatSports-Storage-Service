package iuh.fit.storageservice.infrastructure.external.kafka.consumer;

import iuh.fit.storageservice.application.port.StoragePort;
import iuh.fit.storageservice.infrastructure.external.kafka.event.FileMoveCompletedEvent;
import iuh.fit.storageservice.infrastructure.external.kafka.event.FileMoveEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CopyRequest;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileMoveConsumer {

    private final S3TransferManager s3TransferManager;
    private final S3Client s3Client;
    private final StoragePort storagePort;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${cloudflare.r2.bucket}")
    private String bucketName;
    @Value("${spring.kafka.topic.file-move-completed}")
    private String fileMoveCompletedTopic;

    @RetryableTopic(
            backOff = @BackOff(delay = 2000, multiplier = 2.0)
    )
    @KafkaListener(
            topics = "${spring.kafka.topic.file-move}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(FileMoveEvent event) {
        log.info("Received FileMoveEvent from Kafka containing {} files", event.getRequests().size());
        log.info("Starting move files for correlation: {}", event.getCorrelationId());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<? extends Future<?>> moves = event.getRequests().stream()
                    .map(request -> executor.submit(() -> moveFile(request.tempKey())))
                    .toList();

            for (Future<?> move : moves) {
                move.get();
            }

            kafkaTemplate.send(fileMoveCompletedTopic, event.getCorrelationId(), FileMoveCompletedEvent.builder()
                    .correlationId(event.getCorrelationId())
                    .success(true)
                    .build()).join();
            log.info("Published FileMoveCompletedEvent for correlation: {}", event.getCorrelationId());

        } catch (Exception e) {
            log.error("Error executing parallel file move inside Kafka Consumer", e);
            throw new IllegalStateException("Unable to move files for " + event.getCorrelationId(), e);
        }
    }

    private void moveFile(String tempKey) {
        if (tempKey == null || !tempKey.startsWith("temp/")) {
            throw new IllegalArgumentException("Invalid temporary object key");
        }
        String destinationKey = tempKey.substring("temp/".length());

        if (objectExists(destinationKey)) {
            deleteTemporaryFile(tempKey);
            log.info("File was already moved to: {}", destinationKey);
            return;
        }
        if (!objectExists(tempKey)) {
            throw new IllegalStateException("Temporary file does not exist: " + tempKey);
        }

        CopyRequest copyRequest = CopyRequest.builder()
                .copyObjectRequest(
                        CopyObjectRequest.builder()
                                .sourceBucket(bucketName)
                                .sourceKey(tempKey)
                                .destinationBucket(bucketName)
                                .destinationKey(destinationKey)
                                .build())
                .build();

        try {
            log.info("Processing background copy in consumer for: {} -> {}", tempKey, destinationKey);
            s3TransferManager.copy(copyRequest).completionFuture().join();
        } catch (CompletionException exception) {
            if (!isMissingSource(exception) || !objectExists(destinationKey)) {
                throw exception;
            }
            log.info("Concurrent file move already created destination: {}", destinationKey);
        }

        deleteTemporaryFile(tempKey);
        log.info("Successfully moved file to: {}", destinationKey);
    }

    private void deleteTemporaryFile(String tempKey) {
        if (!objectExists(tempKey)) {
            return;
        }
        log.info("Processing background delete in consumer for: {}", tempKey);
        storagePort.delete(tempKey);
    }

    private boolean isMissingSource(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof NoSuchKeyException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @DltHandler
    public void handleDlt(FileMoveEvent event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Event failed all retries and reached DLT topic {}. Payload: {}", topic, event);
        kafkaTemplate.send(fileMoveCompletedTopic, event.getCorrelationId(), FileMoveCompletedEvent.builder()
                .correlationId(event.getCorrelationId())
                .success(false)
                .failureReason("File move failed after all retries")
                .build()).join();
    }

    private boolean objectExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw exception;
        }
    }
}
