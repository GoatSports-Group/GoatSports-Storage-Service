package iuh.fit.storageservice.infrastructure.security.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class JwtFilter implements Filter {

    @Override
    public void doFilter(
            ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain
    ) throws IOException, ServletException
    {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;

        String email = httpRequest.getHeader("X-User-Email");
        String userKeycloakId = httpRequest.getHeader("X-User-KeycloakId");
        String role = httpRequest.getHeader("X-User-Role");
        String authoritiesRaw = httpRequest.getHeader("X-User-Authorities");

        List<SimpleGrantedAuthority> authorities = Collections.emptyList();

        if (authoritiesRaw != null && !authoritiesRaw.isBlank()) {
            authorities = Arrays.stream(authoritiesRaw.split(","))
                    .filter(auth -> !auth.isBlank())
                    .map(SimpleGrantedAuthority::new)
                    .toList();
        }

        if (hasText(role)) {
            List<SimpleGrantedAuthority> roleAuthorities = List.of(
                    new SimpleGrantedAuthority(normalizeRole(role))
            );
            authorities = authorities.isEmpty()
                    ? roleAuthorities
                    : java.util.stream.Stream
                    .concat(authorities.stream(), roleAuthorities.stream())
                    .distinct()
                    .toList();
        }

        if (hasText(email) || hasText(userKeycloakId) || !authorities.isEmpty()) {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(email, null, authorities);
            auth.setDetails(userKeycloakId);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } else SecurityContextHolder.clearContext();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeRole(String role) {
        return role.replaceFirst("^ROLE_", "").toUpperCase();
    }
}
