package iuh.fit.storageservice.application.service;

import iuh.fit.storageservice.application.dto.request.file.PresignedUrlRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadPolicyTest {

    private static final long MB = 1024L * 1024L;

    private List<PresignedUrlRequest> one(String fileName, String contentType, String folder, Long length) {
        return List.of(new PresignedUrlRequest(fileName, contentType, folder, length));
    }

    @Test
    @DisplayName("ảnh đại diện hợp lệ thì đi qua")
    void validAvatarPasses() {
        assertThatCode(() -> UploadPolicy.validate(one("a.png", "image/png", "avatars", 2 * MB)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("thư mục ngoài whitelist bị từ chối")
    void unknownFolderRejected() {
        assertThatThrownBy(() -> UploadPolicy.validate(one("a.png", "image/png", "hacker", 1024L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Thư mục");
    }

    @Test
    @DisplayName("file thực thi không lọt qua dù đặt trong thư mục hợp lệ")
    void executableContentTypeRejected() {
        assertThatThrownBy(() -> UploadPolicy.validate(
                one("payload.exe", "application/x-msdownload", "avatars", 1024L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Định dạng");
    }

    @Test
    @DisplayName("thiếu contentLength bị từ chối vì không áp được trần dung lượng")
    void missingContentLengthRejected() {
        assertThatThrownBy(() -> UploadPolicy.validate(one("a.png", "image/png", "avatars", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kích thước");
    }

    @Test
    @DisplayName("ảnh đại diện vượt 5MB bị từ chối")
    void oversizedAvatarRejected() {
        assertThatThrownBy(() -> UploadPolicy.validate(one("a.png", "image/png", "avatars", 6 * MB)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("giới hạn");
    }

    @Test
    @DisplayName("video chỉ được phép ở thư mục bài đăng, không được ở avatars")
    void videoAllowedOnlyInPostFolder() {
        assertThatCode(() -> UploadPolicy.validate(one("clip.mp4", "video/mp4", "social-posts", 10 * MB)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> UploadPolicy.validate(one("clip.mp4", "video/mp4", "avatars", 20 * MB)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("tên tệp chứa đường dẫn bị từ chối vì được ghép thẳng vào object key")
    void fileNameWithPathRejected() {
        assertThatThrownBy(() -> UploadPolicy.validate(
                one("../../etc/passwd", "image/png", "avatars", 1024L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đường dẫn");
    }

    @Test
    @DisplayName("xin quá 10 đường dẫn một lần bị từ chối")
    void oversizedBatchRejected() {
        List<PresignedUrlRequest> many = java.util.Collections.nCopies(11,
                new PresignedUrlRequest("a.png", "image/png", "avatars", 1024L));
        assertThatThrownBy(() -> UploadPolicy.validate(many))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tối đa");
    }

    @Test
    @DisplayName("danh sách rỗng bị từ chối")
    void emptyRequestRejected() {
        assertThatThrownBy(() -> UploadPolicy.validate(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("club gallery accepts images up to 5MB and rejects larger files")
    void clubGalleryEnforcesImageLimit() {
        assertThatCode(() -> UploadPolicy.validate(
                one("match-day.webp", "image/webp", "clubs/gallery", 5 * MB)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> UploadPolicy.validate(
                one("match-day.webp", "image/webp", "clubs/gallery", 5 * MB + 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");
        assertThatThrownBy(() -> UploadPolicy.validate(
                one("highlight.mp4", "video/mp4", "clubs/gallery", MB)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("PDF được phép ở thư mục venues cho hồ sơ chủ sân")
    void pdfAllowedForVenueDocuments() {
        assertThatCode(() -> UploadPolicy.validate(
                one("giay-phep.pdf", "application/pdf", "venues", 2 * MB)))
                .doesNotThrowAnyException();
    }

    @Test
    void chatMessagesAcceptImagesUpToTenMegabytes() {
        assertThatCode(() -> UploadPolicy.validate(List.of(
                new PresignedUrlRequest("camera.jpg", "image/jpeg", "chat-messages", 9L * 1024 * 1024))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> UploadPolicy.validate(List.of(
                new PresignedUrlRequest("clip.mp4", "video/mp4", "chat-messages", 1024L))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UploadPolicy.validate(List.of(
                new PresignedUrlRequest("big.jpg", "image/jpeg", "chat-messages", 11L * 1024 * 1024))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("hồ sơ chủ sân: ảnh CCCD vào identities, giấy phép PDF vào licenses, tối đa 2 MB")
    void ownerApplicationFoldersAcceptOnlyTheirDocumentTypes() {
        assertThatCode(() -> UploadPolicy.validate(List.of(
                new PresignedUrlRequest("front.png", "image/png", "identities", 2 * MB),
                new PresignedUrlRequest("back.webp", "image/webp", "identities", MB),
                new PresignedUrlRequest("license.pdf", "application/pdf", "licenses", 2 * MB))))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> UploadPolicy.validate(one("id.pdf", "application/pdf", "identities", MB)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Định dạng");
        assertThatThrownBy(() -> UploadPolicy.validate(one("license.png", "image/png", "licenses", MB)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Định dạng");
        assertThatThrownBy(() -> UploadPolicy.validate(one("front.png", "image/png", "identities", 2 * MB + 1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2MB");
    }
}
