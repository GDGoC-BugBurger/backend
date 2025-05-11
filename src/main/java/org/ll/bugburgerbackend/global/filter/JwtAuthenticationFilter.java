package org.ll.bugburgerbackend.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ll.bugburgerbackend.domain.member.entity.Member;
import org.ll.bugburgerbackend.domain.member.service.MemberService;
import org.ll.bugburgerbackend.global.Ut.Ut;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${custom.jwt.secretKey}")
    private String jwtSecretKey;

    private final MemberService memberService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractToken(request);
        log.debug("Request URI: {}", request.getRequestURI());
        log.debug("Extracted token: {}", token);

        if (token != null) {
            try {
                log.debug("Validating token with secret key: {}", jwtSecretKey.substring(0, 10) + "...");
                
                if (!Ut.jwt.isValid(jwtSecretKey, token)) {
                    log.error("Invalid JWT token");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }

                Map<String, Object> payload = Ut.jwt.payload(jwtSecretKey, token);
                log.debug("Token payload: {}", payload);

                if (payload != null) {
                    Long id = ((Number) payload.get("id")).longValue();
                    String username = (String) payload.get("username");
                    log.debug("Token payload - id: {}, username: {}", id, username);

                    Member member = memberService.findById(id)
                            .orElseThrow(() -> {
                                log.error("Member not found for id: {}", id);
                                return new RuntimeException("Member not found");
                            });

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            member,
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("Authentication set for user: {}", username);
                } else {
                    log.error("Failed to parse JWT payload");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
            } catch (Exception e) {
                log.error("JWT token validation failed", e);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        } else {
            log.debug("No token found in request");
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        log.debug("Authorization header: {}", bearerToken);
        
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            log.debug("Extracted token from Bearer: {}", token);
            return token;
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean shouldNotFilter = path.startsWith("/api/members/sign-in") ||
               path.startsWith("/api/members/sign-up") ||
               path.startsWith("/api/members/login") ||
               path.startsWith("/api/members/register") ||
               path.equals("/") ||
               path.equals("/api/members/") ||
               path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/images/") ||
               path.equals("/error");
        
        log.debug("Path: {}, shouldNotFilter: {}", path, shouldNotFilter);
        return shouldNotFilter;
    }
} 