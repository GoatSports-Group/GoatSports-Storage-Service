package iuh.fit.storageservice.infrastructure.security;

import iuh.fit.storageservice.application.dto.request.file.FileRequester;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SecurityContextHelper {

    public Optional<String> getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof String userId) {
            return Optional.of(userId);
        }
        return Optional.empty();
    }

    public FileRequester currentRequester() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return new FileRequester(null, Set.of());
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return new FileRequester(getCurrentUserId().orElse(null), authorities);
    }

}
