package iuh.fit.storageservice.application.dto.request.file;

import java.util.Set;

/** Người đang gọi API công khai; {@code userId} null khi chưa đăng nhập. */
public record FileRequester(String userId, Set<String> authorities) {

    public FileRequester {
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
    }

    public boolean has(String authority) {
        return authorities.contains(authority);
    }
}
