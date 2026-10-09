package iuh.fit.storageservice.application.service;

import iuh.fit.storageservice.application.dto.request.file.PresignedUrlRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Kiểm tra yêu cầu xin pre-signed URL trước khi ký.
 *
 * Pre-signed URL là giấy phép ghi thẳng vào bucket: đã ký rồi thì storage-service
 * không còn chặn được nữa, nên mọi giới hạn phải đặt ở đây. Trước đây chỉ chặn
 * contentLength <= 0, nghĩa là bất kỳ người dùng đã đăng nhập nào cũng xin được
 * URL để tải lên file tùy loại, tùy kích thước, vào thư mục tùy đặt.
 *
 * Fail-closed: folder/MIME không nằm trong whitelist thì từ chối, không đoán.
 */
public final class UploadPolicy {

    private static final Set<String> IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> VIDEO_TYPES =
            Set.of("video/mp4", "video/webm", "video/quicktime");
    private static final Set<String> DOCUMENT_TYPES =
            Set.of("application/pdf");

    private static final long MB = 1024L * 1024L;

    /** Mỗi thư mục tự khai loại file và trần dung lượng của mình. */
    private record FolderRule(Set<String> allowedTypes, long maxBytes) {}

    private static final Map<String, FolderRule> FOLDERS = Map.of(
            "avatars", new FolderRule(IMAGE_TYPES, 2 * MB),
            "venues", new FolderRule(union(IMAGE_TYPES, DOCUMENT_TYPES), 2 * MB),
            "social-posts", new FolderRule(union(IMAGE_TYPES, VIDEO_TYPES), 10 * MB),
            "clubs/logos", new FolderRule(union(IMAGE_TYPES, DOCUMENT_TYPES), 2 * MB),
            "clubs/banners", new FolderRule(union(IMAGE_TYPES, DOCUMENT_TYPES), 2 * MB),
            "clubs/gallery", new FolderRule(IMAGE_TYPES, 5 * MB),
            "chat-messages", new FolderRule(IMAGE_TYPES, 10 * MB),
            "identities", new FolderRule(Set.of("image/jpeg", "image/png", "image/webp"), 2 * MB),
            "licenses", new FolderRule(DOCUMENT_TYPES, 2 * MB)
    );

    private static final int MAX_FILE_NAME_LENGTH = 200;
    private static final int MAX_BATCH = 10;

    private UploadPolicy() {}

    public static void validate(List<PresignedUrlRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Danh sách tệp tải lên không được rỗng.");
        }
        if (requests.size() > MAX_BATCH) {
            throw new IllegalArgumentException("Mỗi lần chỉ xin được tối đa " + MAX_BATCH + " đường dẫn tải lên.");
        }
        requests.forEach(UploadPolicy::validateOne);
    }

    private static void validateOne(PresignedUrlRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Yêu cầu tải lên không hợp lệ.");
        }

        FolderRule rule = FOLDERS.get(request.folder());
        if (rule == null) {
            throw new IllegalArgumentException("Thư mục tải lên không được hỗ trợ: " + request.folder());
        }

        String contentType = request.contentType() == null ? "" : request.contentType().trim().toLowerCase();
        if (!rule.allowedTypes().contains(contentType)) {
            throw new IllegalArgumentException("Định dạng tệp không được hỗ trợ: " + request.contentType());
        }

        // contentLength bắt buộc: thiếu nó thì không có trần dung lượng nào áp được.
        Long contentLength = request.contentLength();
        if (contentLength == null || contentLength <= 0) {
            throw new IllegalArgumentException("Kích thước tệp không hợp lệ.");
        }
        if (contentLength > rule.maxBytes()) {
            throw new IllegalArgumentException(
                    "Tệp vượt quá giới hạn " + (rule.maxBytes() / MB) + "MB của thư mục " + request.folder() + ".");
        }

        validateFileName(request.fileName());
    }

    /**
     * fileName được ghép thẳng vào object key nên phải là tên tệp trần:
     * dấu phân cách đường dẫn hoặc ký tự điều khiển sẽ lái key ra khỏi khu vực của người dùng.
     */
    private static void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Tên tệp không được để trống.");
        }
        if (fileName.length() > MAX_FILE_NAME_LENGTH) {
            throw new IllegalArgumentException("Tên tệp quá dài.");
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("Tên tệp chứa ký tự đường dẫn không hợp lệ.");
        }
        for (int index = 0; index < fileName.length(); index++) {
            if (Character.isISOControl(fileName.charAt(index))) {
                throw new IllegalArgumentException("Tên tệp chứa ký tự điều khiển không hợp lệ.");
            }
        }
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        return java.util.stream.Stream.concat(left.stream(), right.stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
