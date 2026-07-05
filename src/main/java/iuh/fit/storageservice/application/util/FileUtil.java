package iuh.fit.storageservice.application.util;

import iuh.fit.storageservice.application.exception.ServiceException;

import java.util.regex.Pattern;

public class FileUtil {
    public static final long MAX_FILE_SIZE = 2L * 1024 * 1024;
    private static final Pattern FILE_PATTERN = Pattern.compile("(?i).+\\.(jpg|jpeg|png|pdf|doc|docx)$");

    public static void assertAllowed(String fileName, byte[] content, String contentType) throws ServiceException {
        if (content == null || content.length == 0) {
            throw new ServiceException("File không được rỗng");
        }

        if (content.length > MAX_FILE_SIZE) {
            throw new ServiceException("Kích thước file vượt quá giới hạn 2MB");
        }

        if (fileName == null || fileName.isBlank()) {
            throw new ServiceException("Tên file không được rỗng");
        }

        String normalized = fileName.trim().replaceAll("\\s+", "_");
        if (!FILE_PATTERN.matcher(normalized).matches()) {
            throw new ServiceException("Chỉ cho phép jpg, jpeg, png, pdf, doc, docx files");
        }

        if (contentType == null || contentType.isBlank()) {
            throw new ServiceException("Content type không được rỗng");
        }
    }

}
