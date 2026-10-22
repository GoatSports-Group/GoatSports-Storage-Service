package iuh.fit.storageservice.application.service;

import iuh.fit.storageservice.application.dto.request.file.FileRequester;
import iuh.fit.storageservice.application.exception.ForbiddenException;
import iuh.fit.storageservice.application.port.ChatAttachmentAccessPort;
import iuh.fit.storageservice.application.port.StoragePort;
import iuh.fit.storageservice.application.service.FileAccessPolicy.Decision;
import iuh.fit.storageservice.application.service.impl.StorageServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileAccessPolicyTest {

    private static final String OWNER = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER = "22222222-2222-2222-2222-222222222222";
    private static final String IDENTITY = "identities/" + OWNER + "/abc-front.png";
    private static final String CHAT = "chat-messages/" + OWNER + "/abc-photo.png";

    private static FileRequester user(String id, String... authorities) {
        return new FileRequester(id, Set.of(authorities));
    }

    @Test
    @DisplayName("ảnh công khai: khách chưa đăng nhập vẫn đọc được")
    void publicFolderIsReadableByAnyone() {
        assertThat(FileAccessPolicy.read("venues/" + OWNER + "/abc-court.png", new FileRequester(null, null)))
                .isEqualTo(Decision.ALLOW);
        assertThat(FileAccessPolicy.read("clubs/logos/" + OWNER + "/abc-logo.png", null)).isEqualTo(Decision.ALLOW);
    }

    @Test
    @DisplayName("CCCD: chủ tệp và người có quyền xem hồ sơ chủ sân đọc được, người khác thì không")
    void ownerDocumentNeedsOwnerOrReviewer() {
        assertThat(FileAccessPolicy.read(IDENTITY, user(OWNER))).isEqualTo(Decision.ALLOW);
        assertThat(FileAccessPolicy.read(IDENTITY, user(OTHER, FileAccessPolicy.OWNER_DOCUMENT_AUTHORITY)))
                .isEqualTo(Decision.ALLOW);
        assertThat(FileAccessPolicy.read(IDENTITY, user(OTHER))).isEqualTo(Decision.DENY);
        assertThat(FileAccessPolicy.read(IDENTITY, new FileRequester(null, null))).isEqualTo(Decision.DENY);
    }

    @Test
    @DisplayName("ảnh chat: chủ tệp và ADMIN đọc thẳng, người khác phải hỏi social-service")
    void chatImageAsksSocialForOthers() {
        assertThat(FileAccessPolicy.read(CHAT, user(OWNER))).isEqualTo(Decision.ALLOW);
        assertThat(FileAccessPolicy.read(CHAT, user(OTHER, FileAccessPolicy.ADMIN_AUTHORITY))).isEqualTo(Decision.ALLOW);
        assertThat(FileAccessPolicy.read(CHAT, user(OTHER))).isEqualTo(Decision.CHECK_CHAT_MEMBERSHIP);
    }

    @Test
    @DisplayName("tệp tạm, key lạ và key lách đường dẫn chỉ chủ tệp hoặc không ai đọc được")
    void tempAndMalformedKeysAreClosed() {
        String temp = "temp/chat-messages/" + OWNER + "/abc-photo.png";
        assertThat(FileAccessPolicy.read(temp, user(OWNER))).isEqualTo(Decision.ALLOW);
        assertThat(FileAccessPolicy.read(temp, user(OTHER, FileAccessPolicy.ADMIN_AUTHORITY))).isEqualTo(Decision.DENY);
        assertThat(FileAccessPolicy.read("venues/../identities/" + OWNER + "/x.png", user(OTHER))).isEqualTo(Decision.DENY);
        assertThat(FileAccessPolicy.read("secret/" + OTHER + "/x.png", user(OTHER))).isEqualTo(Decision.DENY);
    }

    @Test
    @DisplayName("chỉ chủ tệp tự xóa được, kể cả ảnh công khai")
    void onlyOwnerDeletes() {
        assertThat(FileAccessPolicy.canDelete("avatars/" + OWNER + "/abc-me.png", user(OWNER))).isTrue();
        assertThat(FileAccessPolicy.canDelete("avatars/" + OWNER + "/abc-me.png", user(OTHER))).isFalse();
        assertThat(FileAccessPolicy.canDelete(IDENTITY, user(OTHER, FileAccessPolicy.ADMIN_AUTHORITY))).isFalse();
    }

    @Test
    @DisplayName("service: không phải thành viên đoạn chat thì 403 và không cấp URL")
    void serviceRefusesNonMember() throws Exception {
        StoragePort storage = mock(StoragePort.class);
        ChatAttachmentAccessPort chat = mock(ChatAttachmentAccessPort.class);
        StorageServiceImpl service = new StorageServiceImpl(storage, chat);
        when(chat.canView(OTHER, CHAT)).thenReturn(false);

        assertThatThrownBy(() -> service.getFileUrl(CHAT, user(OTHER))).isInstanceOf(ForbiddenException.class);
        verify(storage, never()).getFileUrl(anyString());

        when(chat.canView(OTHER, CHAT)).thenReturn(true);
        when(storage.getFileUrl(CHAT)).thenReturn("signed");
        assertThat(service.getFileUrl(CHAT, user(OTHER))).isEqualTo("signed");
    }
}
