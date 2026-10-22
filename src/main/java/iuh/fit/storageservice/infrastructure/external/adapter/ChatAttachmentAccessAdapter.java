package iuh.fit.storageservice.infrastructure.external.adapter;

import iuh.fit.storageservice.application.port.ChatAttachmentAccessPort;
import iuh.fit.storageservice.infrastructure.external.client.SocialClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAttachmentAccessAdapter implements ChatAttachmentAccessPort {
    private final SocialClient socialClient;

    @Override
    public boolean canView(String userId, String key) {
        try {
            return socialClient.canViewAttachment(userId, key);
        } catch (RuntimeException exception) {
            log.warn("Could not check chat attachment access for user {}: {}", userId, exception.getMessage());
            return false;
        }
    }
}
