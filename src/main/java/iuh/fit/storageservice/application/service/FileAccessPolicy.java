package iuh.fit.storageservice.application.service;

import iuh.fit.storageservice.application.dto.request.file.FileRequester;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Ai được đọc / xóa một object key.
 *
 * Key do {@code generatePresignedUrl} tạo có dạng {@code [temp/]{folder}/{ownerId}/{uuid}-{fileName}}.
 * Thư mục công khai (ảnh sân, avatar, CLB, bài viết) ai cũng đọc được. Thư mục riêng tư chỉ chủ tệp đọc được,
 * cộng thêm: giấy tờ chủ sân cho người có quyền xem hồ sơ chủ sân, ảnh chat cho ADMIN và thành viên đoạn chat.
 * Trước đây ai biết key là tải / xin URL đọc / xóa được tệp của người khác.
 *
 * Fail-closed: key lạ, thư mục không biết hoặc không xác định được chủ tệp thì chỉ người có quyền đặc biệt mới qua.
 */
public final class FileAccessPolicy {

    /** Trùng quyền của trang "Hồ sơ chủ sân" ở venue-service (xem chi tiết hồ sơ). */
    public static final String OWNER_DOCUMENT_AUTHORITY = "GET_OWNER_APPLICATION";
    public static final String ADMIN_AUTHORITY = "ROLE_ADMIN";
    public static final String CHAT_FOLDER = "chat-messages";

    private static final String TEMP_PREFIX = "temp/";
    private static final List<String> PUBLIC_FOLDERS = List.of("avatars", "venues", "clubs", "social-posts");
    private static final Set<String> OWNER_DOCUMENT_FOLDERS = Set.of("identities", "licenses");
    /** Thư mục dài trước để "clubs/logos" không bị hiểu là "clubs" + chủ tệp "logos". */
    private static final List<String> FOLDERS = UploadPolicy.folders().stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();

    public enum Decision { ALLOW, CHECK_CHAT_MEMBERSHIP, DENY }

    private FileAccessPolicy() {}

    public static boolean isPublic(String key) {
        return isWellFormed(key) && PUBLIC_FOLDERS.stream().anyMatch(folder -> key.startsWith(folder + "/"));
    }

    public static Decision read(String key, FileRequester requester) {
        if (!isWellFormed(key)) return Decision.DENY;
        if (isPublic(key)) return Decision.ALLOW;
        if (requester == null || requester.userId() == null) return Decision.DENY;
        if (isOwner(key, requester)) return Decision.ALLOW;

        String folder = folderOf(key).orElse(null);
        if (folder != null && !key.startsWith(TEMP_PREFIX)) {
            if (OWNER_DOCUMENT_FOLDERS.contains(folder) && requester.has(OWNER_DOCUMENT_AUTHORITY)) {
                return Decision.ALLOW;
            }
            if (CHAT_FOLDER.equals(folder)) {
                return requester.has(ADMIN_AUTHORITY) ? Decision.ALLOW : Decision.CHECK_CHAT_MEMBERSHIP;
            }
        }
        return Decision.DENY;
    }

    /** Chỉ chủ tệp tự xóa qua API công khai; dọn tệp hộ người khác đi qua /internal của service sở hữu dữ liệu. */
    public static boolean canDelete(String key, FileRequester requester) {
        return isWellFormed(key) && requester != null && requester.userId() != null && isOwner(key, requester);
    }

    static Optional<String> ownerId(String key) {
        String path = key.startsWith(TEMP_PREFIX) ? key.substring(TEMP_PREFIX.length()) : key;
        return FOLDERS.stream()
                .filter(folder -> path.startsWith(folder + "/"))
                .findFirst()
                .map(folder -> path.substring(folder.length() + 1))
                .filter(rest -> rest.indexOf('/') > 0)
                .map(rest -> rest.substring(0, rest.indexOf('/')));
    }

    private static Optional<String> folderOf(String key) {
        return FOLDERS.stream().filter(folder -> key.startsWith(folder + "/")).findFirst();
    }

    private static boolean isOwner(String key, FileRequester requester) {
        return ownerId(key).filter(requester.userId()::equalsIgnoreCase).isPresent();
    }

    private static boolean isWellFormed(String key) {
        return key != null && !key.isBlank() && !key.startsWith("/") && !key.contains("..") && !key.contains("\\");
    }
}
