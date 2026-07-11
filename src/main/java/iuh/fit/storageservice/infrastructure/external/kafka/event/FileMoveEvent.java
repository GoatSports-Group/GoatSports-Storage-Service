package iuh.fit.storageservice.infrastructure.external.kafka.event;

import iuh.fit.storageservice.application.dto.request.file.FileMoveRequest;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileMoveEvent {
    private List<FileMoveRequest> requests;
}
