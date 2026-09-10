package iuh.fit.storageservice.infrastructure.external.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileMoveCompletedEvent {
    private String correlationId;
    private boolean success;
    private String failureReason;
}
