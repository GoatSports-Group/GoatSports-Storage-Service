package iuh.fit.storageservice.infrastructure.external.kafka.consumer;

import iuh.fit.storageservice.application.port.StoragePort;
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
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CopyRequest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileMoveConsumer {

    private final S3TransferManager s3TransferManager;
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
        log.info("Starting move files for process: {}", event.getProcessInstanceKey());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            event.getRequests().forEach(request -> executor.submit(() -> {
                String destinationKey = request.tempKey().replace("temp/", "");

                CopyRequest copyRequest = CopyRequest.builder()
                        .copyObjectRequest(
                                CopyObjectRequest.builder()
                                        .sourceBucket(bucketName)
                                        .sourceKey(request.tempKey())
                                        .destinationBucket(bucketName)
                                        .destinationKey(destinationKey)
                                        .build())
                        .build();

                log.info("Processing background copy in consumer for: {} -> {}", request.tempKey(), destinationKey);
                s3TransferManager.copy(copyRequest).completionFuture().join();

                log.info("Processing background delete in consumer for: {}", request.tempKey());
                storagePort.delete(request.tempKey());

                log.info("Successfully moved file to: {}", destinationKey);
            }));

            kafkaTemplate.send(fileMoveCompletedTopic, event.getProcessInstanceKey());
            log.info("Published FileMoveCompletedEvent for process: {}", event.getProcessInstanceKey());

        } catch (Exception e) {
            log.error("Error executing parallel file move inside Kafka Consumer", e);
            throw e;
        }
    }

    @DltHandler
    public void handleDlt(FileMoveEvent event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Event failed all retries and reached DLT topic {}. Payload: {}", topic, event);
    }
}
