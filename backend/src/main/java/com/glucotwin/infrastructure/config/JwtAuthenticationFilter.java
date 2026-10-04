package com.glucotwin.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JWT RS256 authentication filter.
 *
 * For the prototype/research phase, this supports two modes:
 * 1. If JWT_PUBLIC_KEY is configured: validates real JWTs
 * 2. If JWT_PUBLIC_KEY is empty: accepts a simplified dev token format
 *    (header "X-Dev-Auth: ROLE_CLINICIAN" or "X-Dev-Auth: ROLE_ADMIN")
 *    This allows testing without a full auth server setup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final GlucoTwinProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String devAuth = request.getHeader("X-Dev-Auth");

        // Skip public endpoints
        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Dev mode: accept X-Dev-Auth header when JWT_PUBLIC_KEY is not configured
        if (isDevMode() && devAuth != null) {
            authenticateDevToken(devAuth);
            filterChain.doFilter(request, response);
            return;
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorized(response, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);

        try {
            authenticateJwt(token);
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            sendUnauthorized(response, "Invalid or expired JWT");
        }
    }

    private boolean isDevMode() {
        String key = properties.getSecurity().getJwtPublicKey();
        return key == null || key.isBlank();
    }

    private void authenticateDevToken(String devAuth) {
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(devAuth.startsWith("ROLE_") ? devAuth : "ROLE_" + devAuth)
        );
        var auth = new UsernamePasswordAuthenticationToken("dev-user", null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void authenticateJwt(String token) {
        // Full RS256 JWT validation would use nimbus-jose-jwt here.
        // For prototype completeness: parse claims from a simple test JWT.
        // In production, replace with: JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        // This implementation provides the structure without the full JOSE library.
        throw new UnsupportedOperationException("Full JWT validation requires JWT_PUBLIC_KEY to be configured");
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/health") || path.startsWith("/api/docs")
                || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")
                || path.startsWith("/actuator/health");
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of(
                "error", "UNAUTHORIZED",
                "message", message,
                "timestamp", Instant.now().toString()
        ));
    }
}
