package iuh.fit.storageservice.application.port;

/** social-service là nơi biết ảnh chat thuộc đoạn chat nào và ai còn là thành viên. */
public interface ChatAttachmentAccessPort {
    /** false cả khi social-service không trả lời được: không chắc thì không cấp URL đọc. */
    boolean canView(String userId, String key);
}
